package com.telegram.codex.integration.telegram.domain;

public final class TelegramPayloadValueReader {

    private TelegramPayloadValueReader() {
        // Utility class
    }

    public static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
