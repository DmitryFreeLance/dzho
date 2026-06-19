package ru.dzho.vkbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.dzho.vkbot.model.callback.WallReplyObject;
import ru.dzho.vkbot.model.vk.VkIncomingMessage;

@Service
public class LongPollUpdateDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LongPollUpdateDispatcher.class);

    private final ObjectMapper objectMapper;
    private final VoteService voteService;
    private final MessageService messageService;

    public LongPollUpdateDispatcher(ObjectMapper objectMapper, VoteService voteService, MessageService messageService) {
        this.objectMapper = objectMapper;
        this.voteService = voteService;
        this.messageService = messageService;
    }

    public void dispatch(JsonNode update) {
        String type = update.path("type").asText();
        JsonNode objectNode = update.path("object");

        try {
            switch (type) {
                case "wall_reply_new" -> voteService.processWallReply(objectMapper.treeToValue(objectNode, WallReplyObject.class));
                case "message_new" -> {
                    JsonNode messageNode = objectNode.path("message");
                    if (!messageNode.isMissingNode() && !messageNode.isNull()) {
                        messageService.processIncomingMessage(objectMapper.treeToValue(messageNode, VkIncomingMessage.class));
                    }
                }
                default -> log.debug("Ignoring long poll event type {}", type);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to process VK long poll event " + type, ex);
        }
    }
}

