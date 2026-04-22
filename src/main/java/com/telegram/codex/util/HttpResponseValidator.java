package com.telegram.codex.util;

import java.util.Map;

public final class HttpResponseValidator {

    private HttpResponseValidator() {
        // Utility class
    }

    public static void validateStatusCode(int statusCode, String operation) {
        if (statusCode / 100 != 2) {
            throw new IllegalStateException("Failed to " + operation + ": HTTP " + statusCode);
        }
    }

    public static void validateTelegramResponse(Map<String, Object> payload, String operation) {
        if (payload == null || !Boolean.TRUE.equals(payload.get("ok"))) {
            throw new IllegalStateException("Failed to " + operation + ": invalid response");
        }
    }
}
