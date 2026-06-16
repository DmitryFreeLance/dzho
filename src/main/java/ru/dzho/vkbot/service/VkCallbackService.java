package ru.dzho.vkbot.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.dzho.vkbot.config.VkBotProperties;
import ru.dzho.vkbot.model.callback.VkCallbackPayload;
import ru.dzho.vkbot.model.callback.WallReplyObject;

@Service
public class VkCallbackService {

    private final VkBotProperties properties;
    private final VoteService voteService;

    public VkCallbackService(VkBotProperties properties, VoteService voteService) {
        this.properties = properties;
        this.voteService = voteService;
    }

    public String handle(VkCallbackPayload payload) {
        validateSecret(payload);
        validateGroup(payload);

        if ("confirmation".equals(payload.type())) {
            return properties.confirmationCode();
        }

        if ("wall_reply_new".equals(payload.type())) {
            WallReplyObject object = payload.object();
            if (object != null) {
                voteService.processWallReply(object);
            }
        }

        return "ok";
    }

    private void validateSecret(VkCallbackPayload payload) {
        String expectedSecret = properties.callbackSecret();
        if (expectedSecret == null || expectedSecret.isBlank()) {
            return;
        }
        if (!expectedSecret.equals(payload.secret())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid callback secret");
        }
    }

    private void validateGroup(VkCallbackPayload payload) {
        if (payload.groupId() == null) {
            return;
        }
        if (payload.groupId() != properties.groupId()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unexpected group id");
        }
    }
}

