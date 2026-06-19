package ru.dzho.vkbot.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.dzho.vkbot.model.db.GiftRecord;
import ru.dzho.vkbot.model.db.GiftStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class GiftRepository {

    private final JdbcClient jdbcClient;

    public GiftRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long countAll() {
        Long count = jdbcClient.sql("SELECT COUNT(*) FROM gifts")
                .query(Long.class)
                .single();
        return count == null ? 0 : count;
    }

    public void insert(long giftNumber, String giftName) {
        jdbcClient.sql("""
                        INSERT INTO gifts (
                            gift_number,
                            gift_name,
                            status
                        ) VALUES (
                            :giftNumber,
                            :giftName,
                            'AVAILABLE'
                        )
                        """)
                .param("giftNumber", giftNumber)
                .param("giftName", giftName)
                .update();
    }

    public Optional<GiftRecord> findAssignedToUser(long userId) {
        return jdbcClient.sql("""
                        SELECT gift_number,
                               gift_name,
                               status,
                               assigned_user_id,
                               assigned_at,
                               issued_at
                        FROM gifts
                        WHERE assigned_user_id = :userId
                        LIMIT 1
                        """)
                .param("userId", userId)
                .query(this::map)
                .optional();
    }

    public Optional<GiftRecord> findNextAvailable() {
        return jdbcClient.sql("""
                        SELECT gift_number,
                               gift_name,
                               status,
                               assigned_user_id,
                               assigned_at,
                               issued_at
                        FROM gifts
                        WHERE status = 'AVAILABLE'
                        ORDER BY gift_number
                        LIMIT 1
                        """)
                .query(this::map)
                .optional();
    }

    public void assignGift(long giftNumber, long userId, OffsetDateTime assignedAt) {
        jdbcClient.sql("""
                        UPDATE gifts
                        SET status = 'ASSIGNED',
                            assigned_user_id = :userId,
                            assigned_at = :assignedAt
                        WHERE gift_number = :giftNumber
                          AND status = 'AVAILABLE'
                        """)
                .param("giftNumber", giftNumber)
                .param("userId", userId)
                .param("assignedAt", assignedAt.toString())
                .update();
    }

    private GiftRecord map(ResultSet rs, int rowNum) throws SQLException {
        return new GiftRecord(
                rs.getLong("gift_number"),
                rs.getString("gift_name"),
                GiftStatus.valueOf(rs.getString("status")),
                rs.getObject("assigned_user_id") == null ? null : rs.getLong("assigned_user_id"),
                rs.getString("assigned_at") == null ? null : OffsetDateTime.parse(rs.getString("assigned_at")),
                rs.getString("issued_at") == null ? null : OffsetDateTime.parse(rs.getString("issued_at"))
        );
    }
}

