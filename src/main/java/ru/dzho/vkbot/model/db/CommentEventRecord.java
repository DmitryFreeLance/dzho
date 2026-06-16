package ru.dzho.vkbot.model.db;

import java.time.OffsetDateTime;

public record CommentEventRecord(
        long commentId,
        long userId,
        long ownerId,
        long postId,
        Long parentCommentId,
        String commentText,
        String artTable,
        OffsetDateTime vkCreatedAt,
        OffsetDateTime receivedAt,
        CommentEventStatus status,
        String details,
        Long replyCommentId
) {
}

