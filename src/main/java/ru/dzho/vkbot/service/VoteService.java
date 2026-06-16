package ru.dzho.vkbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import ru.dzho.vkbot.client.VkApiClient;
import ru.dzho.vkbot.client.VkApiException;
import ru.dzho.vkbot.config.VkBotProperties;
import ru.dzho.vkbot.model.callback.WallReplyObject;
import ru.dzho.vkbot.model.db.CommentEventRecord;
import ru.dzho.vkbot.model.db.CommentEventStatus;
import ru.dzho.vkbot.model.db.ParticipantRecord;
import ru.dzho.vkbot.model.vk.VkCommentReplyResult;
import ru.dzho.vkbot.model.vk.VkUserProfile;
import ru.dzho.vkbot.repository.CommentEventRepository;
import ru.dzho.vkbot.repository.ParticipantRepository;
import ru.dzho.vkbot.util.TemplateRenderer;
import ru.dzho.vkbot.util.WriteLinkBuilder;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

@Service
public class VoteService {

    private static final Logger log = LoggerFactory.getLogger(VoteService.class);

    private final VkBotProperties properties;
    private final VkApiClient vkApiClient;
    private final ParticipantRepository participantRepository;
    private final CommentEventRepository commentEventRepository;

    public VoteService(
            VkBotProperties properties,
            VkApiClient vkApiClient,
            ParticipantRepository participantRepository,
            CommentEventRepository commentEventRepository
    ) {
        this.properties = properties;
        this.vkApiClient = vkApiClient;
        this.participantRepository = participantRepository;
        this.commentEventRepository = commentEventRepository;
    }

    public void processWallReply(WallReplyObject reply) {
        Optional<CommentEventRecord> existing = commentEventRepository.findByCommentId(reply.id());
        if (existing.isPresent() && isTerminal(existing.get().status())) {
            log.info("Skipping already processed comment {}", reply.id());
            return;
        }

        OffsetDateTime vkCreatedAt = OffsetDateTime.ofInstant(Instant.ofEpochSecond(reply.date()), ZoneOffset.UTC);
        OffsetDateTime receivedAt = OffsetDateTime.now(ZoneOffset.UTC);
        String artTable = resolveArtTable(reply.postId());

        if (existing.isEmpty()) {
            try {
                commentEventRepository.insertPending(new CommentEventRecord(
                        reply.id(),
                        reply.fromId(),
                        reply.ownerId(),
                        reply.postId(),
                        reply.replyToComment(),
                        reply.text(),
                        artTable,
                        vkCreatedAt,
                        receivedAt,
                        CommentEventStatus.PENDING,
                        null,
                        null
                ));
            } catch (DataIntegrityViolationException ex) {
                log.info("Concurrent duplicate callback ignored for comment {}", reply.id());
                return;
            }
        }

        try {
            doProcess(reply, artTable, vkCreatedAt, receivedAt);
        } catch (RuntimeException ex) {
            commentEventRepository.updateStatus(reply.id(), CommentEventStatus.ERROR, ex.getMessage(), null);
            throw ex;
        }
    }

    private void doProcess(WallReplyObject reply, String artTable, OffsetDateTime vkCreatedAt, OffsetDateTime receivedAt) {
        if (reply.fromId() <= 0) {
            commentEventRepository.updateStatus(reply.id(), CommentEventStatus.IGNORED_NON_USER, "Non-user author", null);
            return;
        }

        if (Math.abs(reply.ownerId()) != properties.groupId()) {
            commentEventRepository.updateStatus(reply.id(), CommentEventStatus.IGNORED_NON_USER, "Unexpected owner_id", null);
            return;
        }

        String link = WriteLinkBuilder.build(properties.writeLinkBase(), "post_" + reply.postId() + "_comment_" + reply.id());
        String groupMention = "[club" + properties.effectivePublicGroupId() + "|сообщение сообщества]";

        Map<String, String> templateValues = Map.of(
                "link", link,
                "artTable", artTable,
                "postId", Long.toString(reply.postId()),
                "profileUrl", "https://vk.com/id" + reply.fromId(),
                "groupMention", groupMention
        );

        if (participantRepository.existsByUserId(reply.fromId())) {
            participantRepository.incrementDuplicateCount(reply.fromId(), reply.id());
            Long replyCommentId = null;
            if (Boolean.TRUE.equals(properties.replyOnDuplicate())) {
                String duplicateMessage = TemplateRenderer.render(properties.duplicateTemplate(), templateValues);
                VkCommentReplyResult result = sendReply(reply, duplicateMessage, TemplateRenderer.render(properties.fallbackTemplate(), templateValues));
                replyCommentId = result.commentId();
            }
            commentEventRepository.updateStatus(reply.id(), CommentEventStatus.DUPLICATE_USER, "Duplicate user comment", replyCommentId);
            return;
        }

        VkUserProfile profile = vkApiClient.getUserProfile(reply.fromId());
        String acceptedMessage = TemplateRenderer.render(properties.replyTemplate(), templateValues);
        String fallbackMessage = TemplateRenderer.render(properties.fallbackTemplate(), templateValues);
        VkCommentReplyResult replyResult = sendReply(reply, acceptedMessage, fallbackMessage);

        participantRepository.insert(new ParticipantRecord(
                reply.fromId(),
                profile.firstName(),
                profile.lastName(),
                profile.screenName(),
                profile.profileUrl(),
                profile.photo200(),
                reply.ownerId(),
                reply.postId(),
                artTable,
                reply.id(),
                reply.text(),
                vkCreatedAt,
                replyResult.commentId(),
                link,
                replyResult.mode(),
                reply.id(),
                receivedAt
        ));

        commentEventRepository.updateStatus(reply.id(), CommentEventStatus.ACCEPTED, "Accepted", replyResult.commentId());
    }

    private VkCommentReplyResult sendReply(WallReplyObject reply, String message, String fallbackMessage) {
        try {
            Long commentId = vkApiClient.createCommentReply(
                    reply.ownerId(),
                    reply.postId(),
                    reply.id(),
                    properties.groupId(),
                    message
            );
            return new VkCommentReplyResult(commentId, "DIRECT_LINK");
        } catch (VkApiException ex) {
            if (ex.code() == 222 && Boolean.TRUE.equals(properties.enableHyperlinkFallback())) {
                Long commentId = vkApiClient.createCommentReply(
                        reply.ownerId(),
                        reply.postId(),
                        reply.id(),
                        properties.groupId(),
                        fallbackMessage
                );
                return new VkCommentReplyResult(commentId, "FALLBACK_NO_LINK");
            }
            throw ex;
        }
    }

    private String resolveArtTable(long postId) {
        return properties.trackedPostsMap().getOrDefault(postId, "Пост " + postId);
    }

    private boolean isTerminal(CommentEventStatus status) {
        return status == CommentEventStatus.ACCEPTED
                || status == CommentEventStatus.DUPLICATE_USER
                || status == CommentEventStatus.IGNORED_NON_USER;
    }
}
