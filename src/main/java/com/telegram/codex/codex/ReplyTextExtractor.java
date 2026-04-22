package com.telegram.codex.codex;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ReplyTextExtractor {

    public String extractReplyText(Object payload, String rawReply) {
        if (payload instanceof Map<?, ?> map) {
            Object text = map.get("text");
            if (text instanceof String stringText && !stringText.isBlank()) {
                return normalizeReplyText(stringText);
            }
            return map.values().stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .max(String::compareTo)
                .map(this::normalizeReplyText)
                .orElseGet(() -> fallbackReplyText(rawReply));
        }
        return fallbackReplyText(rawReply);
    }

    public String fallbackReplyText(String rawReply) {
        String normalized = normalizeReplyText(rawReply == null ? "" : rawReply);
        if (normalized.isBlank()) {
            throw new IllegalStateException("codex exec returned an empty reply");
        }
        return normalized;
    }

    private String normalizeReplyText(String value) {
        return value.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\t", "\t").trim();
    }
}
