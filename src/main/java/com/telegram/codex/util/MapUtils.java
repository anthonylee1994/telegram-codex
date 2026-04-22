package com.telegram.codex.util;

import java.util.List;
import java.util.Map;

public final class MapUtils {

    private MapUtils() {
        // Utility class
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> castListOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
            .filter(item -> item instanceof Map<?, ?>)
            .map(item -> (Map<String, Object>) item)
            .toList();
    }

    public static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static long longValue(Object value) {
        return ((Number) value).longValue();
    }

    public static Long longObjectValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
