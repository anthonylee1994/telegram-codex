package com.telegram.codex.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonSerializer {

    private JsonSerializer() {
        // Utility class
    }

    public static String serialize(ObjectMapper objectMapper, Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Failed to serialize JSON", error);
        }
    }

    public static <T> T deserialize(ObjectMapper objectMapper, String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Failed to deserialize JSON", error);
        }
    }
}
