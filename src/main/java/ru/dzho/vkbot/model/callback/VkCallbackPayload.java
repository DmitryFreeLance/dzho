package ru.dzho.vkbot.model.callback;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VkCallbackPayload(
        String type,
        String secret,
        @JsonProperty("group_id") Long groupId,
        WallReplyObject object
) {
}

