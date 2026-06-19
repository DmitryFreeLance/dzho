package ru.dzho.vkbot.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.dzho.vkbot.model.db.MessageEventStatus;

@Repository
public class MessageEventRepository {

    private final JdbcClient jdbcClient;

    public MessageEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean existsByMessageId(long messageId) {
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM message_events WHERE message_id = :messageId")
                .param("messageId", messageId)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    public void insertPending(long messageId, long peerId, long userId, String messageText, String receivedAt) {
        jdbcClient.sql("""
                        INSERT INTO message_events (
                            message_id,
                            peer_id,
                            user_id,
                            message_text,
                            received_at,
                            status
                        ) VALUES (
                            :messageId,
                            :peerId,
                            :userId,
                            :messageText,
                            :receivedAt,
                            'PENDING'
                        )
                        """)
                .param("messageId", messageId)
                .param("peerId", peerId)
                .param("userId", userId)
                .param("messageText", messageText)
                .param("receivedAt", receivedAt)
                .update();
    }

    public void updateStatus(long messageId, MessageEventStatus status, String details) {
        jdbcClient.sql("""
                        UPDATE message_events
                        SET status = :status,
                            details = :details
                        WHERE message_id = :messageId
                        """)
                .param("messageId", messageId)
                .param("status", status.name())
                .param("details", details)
                .update();
    }
}
