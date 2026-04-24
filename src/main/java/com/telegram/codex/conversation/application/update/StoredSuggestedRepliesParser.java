package com.telegram.codex.conversation.application.update;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StoredSuggestedRepliesParser {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public StoredSuggestedRepliesParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> parse(String rawSuggestedReplies) {
        if (rawSuggestedReplies == null || rawSuggestedReplies.isBlank()) {
            return List.of();
        }
        try {
            List<String> replies = objectMapper.readValue(rawSuggestedReplies, STRING_LIST);
            return replies == null ? List.of() : replies.stream()
                .filter(reply -> reply != null && !reply.isBlank())
                .map(String::trim)
                .toList();
        } catch (Exception error) {
            return List.of();
        }
    }
}
