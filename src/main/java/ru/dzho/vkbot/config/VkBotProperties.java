package ru.dzho.vkbot.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "vk")
public record VkBotProperties(
        @NotNull VkEventSource eventSource,
        @Positive long groupId,
        @NotBlank String accessToken,
        String confirmationCode,
        String callbackSecret,
        long publicGroupId,
        @NotBlank String apiBaseUrl,
        @NotBlank String apiVersion,
        @Positive int longPollWaitSeconds,
        String giftCatalogPath,
        @NotBlank String writeLinkBase,
        String trackedPosts,
        @NotBlank String replyTemplate,
        @NotBlank String duplicateTemplate,
        @NotBlank String fallbackTemplate,
        @NotBlank String giftIssuedTemplate,
        @NotBlank String giftRepeatTemplate,
        @NotBlank String commentRequiredTemplate,
        @NotBlank String giftsExhaustedTemplate,
        @NotBlank String subscriptionRequiredTemplate,
        @NotNull Boolean replyOnDuplicate,
        @NotNull Boolean enableHyperlinkFallback,
        @Positive int seedGiftsCount
) {

    public long effectivePublicGroupId() {
        return publicGroupId > 0 ? publicGroupId : groupId;
    }

    public boolean longPollEnabled() {
        return eventSource == VkEventSource.LONG_POLL || eventSource == VkEventSource.BOTH;
    }

    public String effectiveGiftCatalogPath() {
        if (giftCatalogPath == null || giftCatalogPath.isBlank()) {
            return null;
        }
        return giftCatalogPath.trim();
    }

    public Map<Long, String> trackedPostsMap() {
        if (trackedPosts == null || trackedPosts.isBlank()) {
            return Collections.emptyMap();
        }

        Map<Long, String> result = new LinkedHashMap<>();
        for (String entry : trackedPosts.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("=", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid VK_TRACKED_POSTS entry: " + trimmed);
            }
            long postId = Long.parseLong(parts[0].trim());
            String artTable = parts[1].trim();
            if (artTable.isEmpty()) {
                throw new IllegalArgumentException("Art table name is empty for post " + postId);
            }
            result.put(postId, artTable);
        }
        return Collections.unmodifiableMap(result);
    }
}
