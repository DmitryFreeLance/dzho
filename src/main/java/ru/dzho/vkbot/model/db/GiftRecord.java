package ru.dzho.vkbot.model.db;

import java.time.OffsetDateTime;

public record GiftRecord(
        long giftNumber,
        String giftName,
        GiftStatus status,
        Long assignedUserId,
        OffsetDateTime assignedAt,
        OffsetDateTime issuedAt
) {
}

