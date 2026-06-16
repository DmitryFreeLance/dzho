package ru.dzho.vkbot.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.dzho.vkbot.model.db.ParticipantRecord;

@Repository
public class ParticipantRepository {

    private final JdbcClient jdbcClient;

    public ParticipantRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean existsByUserId(long userId) {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM participants WHERE user_id = :userId")
                .param("userId", userId)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    public void insert(ParticipantRecord participant) {
        jdbcClient.sql("""
                        INSERT INTO participants (
                            user_id,
                            first_name,
                            last_name,
                            screen_name,
                            profile_url,
                            avatar_url,
                            source_owner_id,
                            source_post_id,
                            art_table,
                            first_comment_id,
                            first_comment_text,
                            first_comment_at,
                            first_reply_comment_id,
                            reply_link,
                            reply_mode,
                            last_seen_comment_id,
                            created_at
                        ) VALUES (
                            :userId,
                            :firstName,
                            :lastName,
                            :screenName,
                            :profileUrl,
                            :avatarUrl,
                            :sourceOwnerId,
                            :sourcePostId,
                            :artTable,
                            :firstCommentId,
                            :firstCommentText,
                            :firstCommentAt,
                            :firstReplyCommentId,
                            :replyLink,
                            :replyMode,
                            :lastSeenCommentId,
                            :createdAt
                        )
                        """)
                .param("userId", participant.userId())
                .param("firstName", participant.firstName())
                .param("lastName", participant.lastName())
                .param("screenName", participant.screenName())
                .param("profileUrl", participant.profileUrl())
                .param("avatarUrl", participant.avatarUrl())
                .param("sourceOwnerId", participant.sourceOwnerId())
                .param("sourcePostId", participant.sourcePostId())
                .param("artTable", participant.artTable())
                .param("firstCommentId", participant.firstCommentId())
                .param("firstCommentText", participant.firstCommentText())
                .param("firstCommentAt", participant.firstCommentAt().toString())
                .param("firstReplyCommentId", participant.firstReplyCommentId())
                .param("replyLink", participant.replyLink())
                .param("replyMode", participant.replyMode())
                .param("lastSeenCommentId", participant.lastSeenCommentId())
                .param("createdAt", participant.createdAt().toString())
                .update();
    }

    public void incrementDuplicateCount(long userId, long lastSeenCommentId) {
        jdbcClient.sql("""
                        UPDATE participants
                        SET duplicate_comments_count = duplicate_comments_count + 1,
                            last_seen_comment_id = :lastSeenCommentId
                        WHERE user_id = :userId
                        """)
                .param("userId", userId)
                .param("lastSeenCommentId", lastSeenCommentId)
                .update();
    }
}

