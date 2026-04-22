package com.telegramcodex.codex;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Transcript {

    private static final int MAX_MESSAGES = 100;

    private final List<Map<String, String>> messages;

    private Transcript(List<Map<String, String>> messages) {
        this.messages = trim(messages);
    }

    public static Transcript empty() {
        return new Transcript(List.of());
    }

    public static Transcript fromConversationState(String conversationState, ObjectMapper objectMapper) {
        if (conversationState == null || conversationState.isBlank()) {
            return empty();
        }
        try {
            List<Map<String, Object>> payload = objectMapper.readValue(conversationState, new TypeReference<>() {
            });
            List<Map<String, String>> messages = payload.stream()
                .filter(message -> List.of("user", "assistant").contains(String.valueOf(message.get("role"))))
                .map(message -> Map.of(
                    "role", String.valueOf(message.get("role")),
                    "content", String.valueOf(message.get("content"))
                ))
                .filter(message -> !message.get("content").isBlank())
                .toList();
            return new Transcript(messages);
        } catch (Exception error) {
            return empty();
        }
    }

    public Transcript append(String role, String content) {
        ArrayList<Map<String, String>> next = new ArrayList<>(messages);
        next.add(Map.of("role", role, "content", content));
        return new Transcript(next);
    }

    public int size() {
        return messages.size();
    }

    public List<String> toPromptLines() {
        ArrayList<String> lines = new ArrayList<>();
        for (int index = 0; index < messages.size(); index += 1) {
            Map<String, String> message = messages.get(index);
            lines.add((index + 1) + ". " + message.get("role") + ": " + message.get("content"));
        }
        return List.copyOf(lines);
    }

    public String toConversationState(ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(messages);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to serialize transcript", error);
        }
    }

    private List<Map<String, String>> trim(List<Map<String, String>> source) {
        if (source.size() <= MAX_MESSAGES) {
            return List.copyOf(source);
        }
        return List.copyOf(source.subList(source.size() - MAX_MESSAGES, source.size()));
    }
}
