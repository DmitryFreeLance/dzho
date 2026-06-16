package ru.dzho.vkbot.model.callback;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WallReplyObject(
        long id,
        @JsonProperty("from_id") long fromId,
        @JsonProperty("owner_id") long ownerId,
        @JsonProperty("post_id") long postId,
        @JsonProperty("reply_to_comment") Long replyToComment,
        long date,
        String text
) {
}

