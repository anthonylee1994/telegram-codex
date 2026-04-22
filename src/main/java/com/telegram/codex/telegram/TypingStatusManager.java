package com.telegram.codex.telegram;

import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Component
public class TypingStatusManager {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    });

    public <T> T withTypingStatus(String chatId, Consumer<String> sendTypingAction, Supplier<T> action) {
        try {
            sendTypingAction.accept(chatId);
        } catch (Exception ignored) {
        }

        ScheduledFuture<?> typingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                sendTypingAction.accept(chatId);
            } catch (Exception ignored) {
            }
        }, 4, 4, TimeUnit.SECONDS);

        try {
            return action.get();
        } finally {
            typingTask.cancel(true);
        }
    }
}
