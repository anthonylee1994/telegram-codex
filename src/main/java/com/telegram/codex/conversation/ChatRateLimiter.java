package com.telegram.codex.conversation;

import com.telegram.codex.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ChatRateLimiter {

    private final AppProperties properties;
    private final Map<String, List<Long>> hits = new HashMap<>();

    public ChatRateLimiter(AppProperties properties) {
        this.properties = properties;
    }

    public synchronized boolean allow(String chatId) {
        long now = System.currentTimeMillis();
        List<Long> freshHits = new ArrayList<>(hits.getOrDefault(chatId, List.of()).stream()
            .filter(timestamp -> now - timestamp < properties.getRateLimitWindowMs())
            .toList());
        if (freshHits.size() >= properties.getRateLimitMaxMessages()) {
            hits.put(chatId, freshHits);
            return false;
        }
        freshHits.add(now);
        hits.put(chatId, freshHits);
        return true;
    }

    public synchronized void reset() {
        hits.clear();
    }
}
