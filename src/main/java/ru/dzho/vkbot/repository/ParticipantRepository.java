package ru.dzho.vkbot.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.dzho.vkbot.model.db.ParticipantRecord;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

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
                            gift_number,
                            gift_name,
                            gift_assigned_at,
                            gift_status,
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
                            :giftNumber,
                            :giftName,
                            :giftAssignedAt,
                            :giftStatus,
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
                .param("giftNumber", participant.giftNumber())
                .param("giftName", participant.giftName())
                .param("giftAssignedAt", participant.giftAssignedAt() == null ? null : participant.giftAssignedAt().toString())
                .param("giftStatus", participant.giftStatus())
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

    public Optional<ParticipantRecord> findByUserId(long userId) {
        return jdbcClient.sql("""
                        SELECT user_id,
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
                               gift_number,
                               gift_name,
                               gift_assigned_at,
                               gift_status,
                               last_seen_comment_id,
                               created_at
                        FROM participants
                        WHERE user_id = :userId
                        """)
                .param("userId", userId)
                .query(this::map)
                .optional();
    }

    public List<ParticipantRecord> findAll() {
        return jdbcClient.sql("""
                        SELECT user_id,
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
                               gift_number,
                               gift_name,
                               gift_assigned_at,
                               gift_status,
                               last_seen_comment_id,
                               created_at
                        FROM participants
                        ORDER BY created_at ASC
                        """)
                .query(this::map)
                .list();
    }

    public void assignGift(long userId, long giftNumber, String giftName, OffsetDateTime assignedAt) {
        jdbcClient.sql("""
                        UPDATE participants
                        SET gift_number = :giftNumber,
                            gift_name = :giftName,
                            gift_assigned_at = :giftAssignedAt,
                            gift_status = 'ASSIGNED'
                        WHERE user_id = :userId
                        """)
                .param("userId", userId)
                .param("giftNumber", giftNumber)
                .param("giftName", giftName)
                .param("giftAssignedAt", assignedAt.toString())
                .update();
    }

    private ParticipantRecord map(ResultSet rs, int rowNum) throws SQLException {
        return new ParticipantRecord(
                rs.getLong("user_id"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("screen_name"),
                rs.getString("profile_url"),
                rs.getString("avatar_url"),
                rs.getLong("source_owner_id"),
                rs.getLong("source_post_id"),
                rs.getString("art_table"),
                rs.getLong("first_comment_id"),
                rs.getString("first_comment_text"),
                OffsetDateTime.parse(rs.getString("first_comment_at")),
                rs.getObject("first_reply_comment_id") == null ? null : rs.getLong("first_reply_comment_id"),
                rs.getString("reply_link"),
                rs.getString("reply_mode"),
                rs.getObject("gift_number") == null ? null : rs.getLong("gift_number"),
                rs.getString("gift_name"),
                rs.getString("gift_assigned_at") == null ? null : OffsetDateTime.parse(rs.getString("gift_assigned_at")),
                rs.getString("gift_status"),
                rs.getLong("last_seen_comment_id"),
                OffsetDateTime.parse(rs.getString("created_at"))
        );
    }
}
