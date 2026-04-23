package com.telegram.codex.integration.codex;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ReplyTextExtractor {

    public String extractReplyText(Object payload, String rawReply) {
        if (payload instanceof Map<?, ?> map) {
            Object text = map.get("text");
            if (text instanceof String stringText && !stringText.isBlank()) {
                return TextNormalizer.normalize(stringText);
            }
            return map.values().stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .max(String::compareTo)
                .map(TextNormalizer::normalize)
                .orElseGet(() -> fallbackReplyText(rawReply));
        }
        return fallbackReplyText(rawReply);
    }

    public String fallbackReplyText(String rawReply) {
        String normalized = TextNormalizer.normalize(rawReply == null ? "" : rawReply);
        if (normalized.isBlank()) {
            throw new IllegalStateException("codex exec returned an empty reply");
        }
        return normalized;
    }
}
