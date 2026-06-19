package ru.dzho.vkbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import ru.dzho.vkbot.client.VkApiClient;
import ru.dzho.vkbot.config.VkBotProperties;
import ru.dzho.vkbot.model.vk.VkLongPollServer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
public class LongPollRunner implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(LongPollRunner.class);

    private final VkBotProperties properties;
    private final VkApiClient vkApiClient;
    private final LongPollUpdateDispatcher dispatcher;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "vk-long-poll-runner");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean running;
    private Future<?> loopFuture;

    public LongPollRunner(VkBotProperties properties, VkApiClient vkApiClient, LongPollUpdateDispatcher dispatcher) {
        this.properties = properties;
        this.vkApiClient = vkApiClient;
        this.dispatcher = dispatcher;
    }

    public void start() {
        if (running || !properties.longPollEnabled()) {
            return;
        }
        running = true;
        loopFuture = executorService.submit(this::runLoop);
        log.info("VK Long Poll runner started");
    }

    private void runLoop() {
        VkLongPollServer server = null;

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                if (server == null) {
                    server = vkApiClient.getLongPollServer();
                }

                JsonNode response = vkApiClient.checkLongPoll(server);
                if (response.has("failed")) {
                    int failed = response.path("failed").asInt();
                    if (failed == 1) {
                        server = server.withTs(response.path("ts").asText(server.ts()));
                    } else {
                        server = null;
                    }
                    continue;
                }

                server = server.withTs(response.path("ts").asText(server.ts()));
                for (JsonNode update : response.path("updates")) {
                    dispatcher.dispatch(update);
                }
            } catch (Exception ex) {
                log.warn("VK Long Poll iteration failed: {}", ex.getMessage());
                server = null;
                sleepQuietly(3000L);
            }
        }
    }

    public void stop() {
        running = false;
        if (loopFuture != null) {
            loopFuture.cancel(true);
        }
        executorService.shutdownNow();
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        start();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void destroy() {
        stop();
    }
}
