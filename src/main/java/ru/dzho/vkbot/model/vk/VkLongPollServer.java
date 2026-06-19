package ru.dzho.vkbot.model.vk;

public record VkLongPollServer(String key, String server, String ts) {

    public VkLongPollServer withTs(String newTs) {
        return new VkLongPollServer(key, server, newTs);
    }
}

