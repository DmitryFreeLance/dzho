package ru.dzho.vkbot.model.vk;

public record VkUserProfile(
        long id,
        String firstName,
        String lastName,
        String screenName,
        String photo200,
        String profileUrl
) {
}

