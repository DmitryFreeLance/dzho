package ru.dzho.vkbot.model.db;

import java.time.OffsetDateTime;

public record ParticipantRecord(
        long userId,
        String firstName,
        String lastName,
        String screenName,
        String profileUrl,
        String avatarUrl,
        long sourceOwnerId,
        long sourcePostId,
        String artTable,
        long firstCommentId,
        String firstCommentText,
        OffsetDateTime firstCommentAt,
        Long firstReplyCommentId,
        String replyLink,
        String replyMode,
        Long giftNumber,
        String giftName,
        OffsetDateTime giftAssignedAt,
        String giftStatus,
        long lastSeenCommentId,
        OffsetDateTime createdAt
) {
}
