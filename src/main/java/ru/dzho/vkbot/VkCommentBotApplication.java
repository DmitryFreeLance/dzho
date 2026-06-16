package ru.dzho.vkbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VkCommentBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(VkCommentBotApplication.class, args);
    }
}

