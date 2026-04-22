package com.telegram.codex.util;

public final class TextNormalizer {

    private TextNormalizer() {
        // Utility class
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .trim();
    }
}
