package ru.dzho.vkbot.model.vk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VkIncomingMessage(
        long id,
        @JsonProperty("peer_id") long peerId,
        @JsonProperty("from_id") long fromId,
        String text,
        String payload
) {
    public VkIncomingMessage(long id, long peerId, long fromId, String text) {
        this(id, peerId, fromId, text, null);
    }
}
