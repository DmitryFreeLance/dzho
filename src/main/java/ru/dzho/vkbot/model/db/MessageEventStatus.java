package ru.dzho.vkbot.model.db;

public enum MessageEventStatus {
    PENDING,
    GIFT_ASSIGNED,
    GIFT_ALREADY_ASSIGNED,
    SUBSCRIPTION_REQUIRED,
    COMMENT_REQUIRED,
    GIFTS_EXHAUSTED,
    ERROR
}
