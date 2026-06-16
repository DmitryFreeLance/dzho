package ru.dzho.vkbot.client;

public class VkApiException extends RuntimeException {

    private final int code;

    public VkApiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}

