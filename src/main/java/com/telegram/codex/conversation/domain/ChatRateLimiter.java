package com.telegram.codex.conversation.domain;

import com.telegram.codex.shared.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ChatRateLimiter {

    private final AppProperties properties;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Long>> hits = new ConcurrentHashMap<>();

    public ChatRateLimiter(AppProperties properties) {
        this.properties = properties;
    }

    public boolean allow(String chatId) {
        long now = System.currentTimeMillis();
        CopyOnWriteArrayList<Long> chatHits = hits.computeIfAbsent(chatId, k -> new CopyOnWriteArrayList<>());

        chatHits.removeIf(timestamp -> now - timestamp >= properties.getRateLimitWindowMs());

        if (chatHits.size() >= properties.getRateLimitMaxMessages()) {
            return false;
        }

        chatHits.add(now);
        return true;
    }

}
