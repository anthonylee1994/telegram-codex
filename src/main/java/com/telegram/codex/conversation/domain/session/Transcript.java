package com.telegram.codex.conversation.domain.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.conversation.domain.ConversationConstants;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public final class Transcript {

    private final List<Entry> messages;

    private Transcript(List<Entry> messages) {
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
            List<Entry> payload = objectMapper.readValue(conversationState, new TypeReference<>() {
            });
            List<Entry> messages = payload.stream()
                .filter(message -> List.of("user", "assistant").contains(message.role()))
                .map(message -> new Entry(message.role(), message.content()))
                .filter(message -> !message.content().isBlank())
                .toList();
            return new Transcript(messages);
        } catch (JsonProcessingException error) {
            return empty();
        }
    }

    public Transcript append(String role, String content) {
        ArrayList<Entry> next = new ArrayList<>(messages);
        next.add(new Entry(role, content));
        return new Transcript(next);
    }

    public int size() {
        return messages.size();
    }

    public List<String> toTaggedPromptLines() {
        ArrayList<String> lines = new ArrayList<>();
        for (int index = 0; index < messages.size(); index += 1) {
            Entry message = messages.get(index);
            String role = message.role();
            lines.add("<message index=\"" + (index + 1) + "\" role=\"" + role + "\">");
            lines.add(message.content());
            lines.add("</message>");
        }
        return List.copyOf(lines);
    }

    public String toConversationState(ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(messages);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Failed to serialize JSON", error);
        }
    }

    private List<Entry> trim(List<Entry> source) {
        if (source.size() <= ConversationConstants.MAX_TRANSCRIPT_MESSAGES) {
            return List.copyOf(source);
        }
        return List.copyOf(source.subList(source.size() - ConversationConstants.MAX_TRANSCRIPT_MESSAGES, source.size()));
    }

    private record Entry(
        @JsonProperty("role") String role,
        @JsonProperty("content") String content
    ) {
        private Entry {
            role = role == null ? "" : role;
            content = content == null ? "" : content;
        }
    }
}
