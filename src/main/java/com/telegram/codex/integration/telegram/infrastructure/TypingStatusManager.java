package com.telegram.codex.integration.telegram.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Component
public class TypingStatusManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TypingStatusManager.class);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    });

    public <T> T withTypingStatus(String chatId, Consumer<String> sendTypingAction, Supplier<T> action) {
        try {
            sendTypingAction.accept(chatId);
        } catch (Exception error) {
            LOGGER.debug("Failed to send initial typing status for chat_id={}", chatId, error);
        }

        ScheduledFuture<?> typingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                sendTypingAction.accept(chatId);
            } catch (Exception error) {
                LOGGER.debug("Failed to send periodic typing status for chat_id={}", chatId, error);
            }
        }, 4, 4, TimeUnit.SECONDS);

        try {
            return action.get();
        } finally {
            typingTask.cancel(true);
        }
    }
}
