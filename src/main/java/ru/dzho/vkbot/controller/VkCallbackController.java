package ru.dzho.vkbot.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.dzho.vkbot.model.callback.VkCallbackPayload;
import ru.dzho.vkbot.service.VkCallbackService;

@RestController
@RequestMapping("/vk")
public class VkCallbackController {

    private final VkCallbackService callbackService;

    public VkCallbackController(VkCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @PostMapping(value = "/callback", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> handleCallback(@RequestBody VkCallbackPayload payload) {
        String response = callbackService.handle(payload);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(response);
    }
}

