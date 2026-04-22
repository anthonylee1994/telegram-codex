package com.telegram.codex.util;

public final class StringUtils {

    private StringUtils() {
        // Utility class
    }

    public static boolean isNullOrBlank(String value) {
        return value == null || value.isBlank();
    }

    public static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
