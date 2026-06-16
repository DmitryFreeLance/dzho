package ru.dzho.vkbot.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.dzho.vkbot.model.db.CommentEventRecord;
import ru.dzho.vkbot.model.db.CommentEventStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class CommentEventRepository {

    private final JdbcClient jdbcClient;

    public CommentEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<CommentEventRecord> findByCommentId(long commentId) {
        return jdbcClient.sql("""
                        SELECT comment_id,
                               user_id,
                               owner_id,
                               post_id,
                               parent_comment_id,
                               comment_text,
                               art_table,
                               vk_created_at,
                               received_at,
                               status,
                               details,
                               reply_comment_id
                        FROM comment_events
                        WHERE comment_id = :commentId
                        """)
                .param("commentId", commentId)
                .query(this::map)
                .optional();
    }

    public void insertPending(CommentEventRecord record) {
        jdbcClient.sql("""
                        INSERT INTO comment_events (
                            comment_id,
                            user_id,
                            owner_id,
                            post_id,
                            parent_comment_id,
                            comment_text,
                            art_table,
                            vk_created_at,
                            received_at,
                            status,
                            details,
                            reply_comment_id
                        ) VALUES (
                            :commentId,
                            :userId,
                            :ownerId,
                            :postId,
                            :parentCommentId,
                            :commentText,
                            :artTable,
                            :vkCreatedAt,
                            :receivedAt,
                            :status,
                            :details,
                            :replyCommentId
                        )
                        """)
                .param("commentId", record.commentId())
                .param("userId", record.userId())
                .param("ownerId", record.ownerId())
                .param("postId", record.postId())
                .param("parentCommentId", record.parentCommentId())
                .param("commentText", record.commentText())
                .param("artTable", record.artTable())
                .param("vkCreatedAt", record.vkCreatedAt().toString())
                .param("receivedAt", record.receivedAt().toString())
                .param("status", record.status().name())
                .param("details", record.details())
                .param("replyCommentId", record.replyCommentId())
                .update();
    }

    public void updateStatus(long commentId, CommentEventStatus status, String details, Long replyCommentId) {
        jdbcClient.sql("""
                        UPDATE comment_events
                        SET status = :status,
                            details = :details,
                            reply_comment_id = :replyCommentId
                        WHERE comment_id = :commentId
                        """)
                .param("status", status.name())
                .param("details", details)
                .param("replyCommentId", replyCommentId)
                .param("commentId", commentId)
                .update();
    }

    private CommentEventRecord map(ResultSet rs, int rowNum) throws SQLException {
        String replyCommentIdRaw = rs.getString("reply_comment_id");
        return new CommentEventRecord(
                rs.getLong("comment_id"),
                rs.getLong("user_id"),
                rs.getLong("owner_id"),
                rs.getLong("post_id"),
                rs.getObject("parent_comment_id") == null ? null : rs.getLong("parent_comment_id"),
                rs.getString("comment_text"),
                rs.getString("art_table"),
                OffsetDateTime.parse(rs.getString("vk_created_at")),
                OffsetDateTime.parse(rs.getString("received_at")),
                CommentEventStatus.valueOf(rs.getString("status")),
                rs.getString("details"),
                replyCommentIdRaw == null ? null : Long.parseLong(replyCommentIdRaw)
        );
    }
}

