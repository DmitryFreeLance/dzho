package ru.dzho.vkbot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.dzho.vkbot.client.VkApiClient;
import ru.dzho.vkbot.config.VkBotProperties;
import ru.dzho.vkbot.model.db.GiftRecord;
import ru.dzho.vkbot.model.db.MessageEventStatus;
import ru.dzho.vkbot.model.db.ParticipantRecord;
import ru.dzho.vkbot.model.vk.VkIncomingMessage;
import ru.dzho.vkbot.repository.GiftRepository;
import ru.dzho.vkbot.repository.MessageEventRepository;
import ru.dzho.vkbot.repository.ParticipantRepository;
import ru.dzho.vkbot.util.TemplateRenderer;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final String SUBSCRIPTION_CHECK_PAYLOAD = "{\"cmd\":\"subscription_check\"}";
    private static final String REMOVE_KEYBOARD = "{\"buttons\":[]}";

    private final VkBotProperties properties;
    private final ParticipantRepository participantRepository;
    private final GiftRepository giftRepository;
    private final MessageEventRepository messageEventRepository;
    private final VkApiClient vkApiClient;
    private final GoogleSheetsSyncService googleSheetsSyncService;
    private final ObjectMapper objectMapper;

    public MessageService(
            VkBotProperties properties,
            ParticipantRepository participantRepository,
            GiftRepository giftRepository,
            MessageEventRepository messageEventRepository,
            VkApiClient vkApiClient,
            GoogleSheetsSyncService googleSheetsSyncService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.participantRepository = participantRepository;
        this.giftRepository = giftRepository;
        this.messageEventRepository = messageEventRepository;
        this.vkApiClient = vkApiClient;
        this.googleSheetsSyncService = googleSheetsSyncService;
        this.objectMapper = objectMapper;
    }

    public synchronized void processIncomingMessage(VkIncomingMessage message) {
        if (message.fromId() <= 0) {
            return;
        }

        if (messageEventRepository.existsByMessageId(message.id())) {
            log.info("Skipping already processed message {}", message.id());
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        messageEventRepository.insertPending(message.id(), message.peerId(), message.fromId(), message.text(), now.toString());

        try {
            Optional<ParticipantRecord> participantOptional = participantRepository.findByUserId(message.fromId());
            if (participantOptional.isEmpty()) {
                vkApiClient.sendMessage(message.peerId(), properties.commentRequiredTemplate());
                messageEventRepository.updateStatus(message.id(), MessageEventStatus.COMMENT_REQUIRED, "No accepted comment found");
                return;
            }

            ParticipantRecord participant = participantOptional.get();
            Optional<GiftRecord> assignedGift = giftRepository.findAssignedToUser(message.fromId());
            if (assignedGift.isPresent()) {
                vkApiClient.sendMessage(
                        message.peerId(),
                        renderGiftTemplate(properties.giftRepeatTemplate(), participant, assignedGift.orElseThrow()),
                        REMOVE_KEYBOARD
                );
                messageEventRepository.updateStatus(message.id(), MessageEventStatus.GIFT_ALREADY_ASSIGNED, "Gift already assigned");
                return;
            }

            if (!vkApiClient.isGroupMember(message.fromId())) {
                vkApiClient.sendMessage(message.peerId(), properties.subscriptionRequiredTemplate(), buildSubscriptionKeyboard());
                messageEventRepository.updateStatus(message.id(), MessageEventStatus.SUBSCRIPTION_REQUIRED, "User is not subscribed to the group");
                return;
            }

            Optional<GiftRecord> nextGiftOptional = giftRepository.findNextAvailable();
            if (nextGiftOptional.isEmpty()) {
                vkApiClient.sendMessage(message.peerId(), properties.giftsExhaustedTemplate());
                messageEventRepository.updateStatus(message.id(), MessageEventStatus.GIFTS_EXHAUSTED, "No gifts left");
                return;
            }

            GiftRecord gift = nextGiftOptional.get();
            giftRepository.assignGift(gift.giftNumber(), message.fromId(), now);
            participantRepository.assignGift(message.fromId(), gift.giftNumber(), gift.giftName(), now);
            ParticipantRecord updatedParticipant = participantRepository.findByUserId(message.fromId())
                    .orElseThrow(() -> new IllegalStateException("Participant disappeared after gift assignment"));
            googleSheetsSyncService.syncParticipant(updatedParticipant);

            vkApiClient.sendMessage(
                    message.peerId(),
                    renderGiftTemplate(properties.giftIssuedTemplate(), updatedParticipant, gift),
                    REMOVE_KEYBOARD
            );
            messageEventRepository.updateStatus(message.id(), MessageEventStatus.GIFT_ASSIGNED, "Gift assigned");
        } catch (RuntimeException ex) {
            messageEventRepository.updateStatus(message.id(), MessageEventStatus.ERROR, ex.getMessage());
            throw ex;
        }
    }

    private String renderGiftTemplate(String template, ParticipantRecord participant, GiftRecord gift) {
        return TemplateRenderer.render(template, Map.of(
                "giftNumber", Long.toString(gift.giftNumber()),
                "giftName", gift.giftName(),
                "artTable", participant.artTable(),
                "postId", Long.toString(participant.sourcePostId())
        ));
    }

    private String buildSubscriptionKeyboard() {
        String subscribeLink = "https://vk.com/club" + properties.effectivePublicGroupId();
        Map<String, Object> keyboard = Map.of(
                "one_time", false,
                "buttons", java.util.List.of(
                        java.util.List.of(
                                Map.of(
                                        "action", Map.of(
                                                "type", "open_link",
                                                "link", subscribeLink,
                                                "label", "Подписаться"
                                        )
                                ),
                                Map.of(
                                        "action", Map.of(
                                                "type", "text",
                                                "label", "Я подписался",
                                                "payload", SUBSCRIPTION_CHECK_PAYLOAD
                                        ),
                                        "color", "primary"
                                )
                        )
                )
        );

        try {
            return objectMapper.writeValueAsString(keyboard);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to build subscription keyboard", ex);
        }
    }
}
