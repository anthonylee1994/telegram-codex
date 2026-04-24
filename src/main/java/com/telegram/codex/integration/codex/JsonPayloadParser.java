package com.telegram.codex.integration.codex;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JsonPayloadParser {

    private static final Pattern TEXT_PATTERN = Pattern.compile("\"text\"\\s*:\\s*\"(?<value>[\\s\\S]*?)\"\\s*,\\s*\"suggested_replies\"\\s*:");
    private static final Pattern REPLIES_PATTERN = Pattern.compile("\"suggested_replies\"\\s*:\\s*\\[(?<value>[\\s\\S]*?)\\]");

    private final ObjectMapper objectMapper;

    public JsonPayloadParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Object parsePayload(String rawReply) {
        for (String candidate : candidatePayloads(rawReply)) {
            if (candidate.isBlank()) {
                continue;
            }
            try {
                Object payload = objectMapper.readValue(candidate, new TypeReference<>() {
                });
                if (payload instanceof String stringPayload) {
                    payload = objectMapper.readValue(stringPayload, new TypeReference<>() {
                    });
                }
                if (payload instanceof Map<?, ?> || payload instanceof List<?>) {
                    return payload;
                }
            } catch (Exception ignored) {
                // keep trying relaxed candidates
            }
        }
        throw new IllegalArgumentException("Reply payload is not JSON");
    }

    private List<String> candidatePayloads(String rawReply) {
        String normalized = rawReply == null ? "" : rawReply.trim();
        String unwrapped = normalized.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        String extracted = extractJsonObject(unwrapped);
        String relaxed = extractRelaxedPayload(extracted == null ? unwrapped : extracted);
        return List.of(normalized, unwrapped, extracted == null ? "" : extracted, relaxed == null ? "" : relaxed);
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    private String extractRelaxedPayload(String text) {
        String replyText = extractRelaxedText(text);
        List<String> suggestedReplies = extractRelaxedSuggestedReplies(text);
        if (replyText.isBlank() && suggestedReplies.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(Map.of("text", replyText, "suggested_replies", suggestedReplies));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Failed to build relaxed payload", error);
        }
    }

    private String extractRelaxedText(String text) {
        Matcher matcher = TEXT_PATTERN.matcher(text == null ? "" : text);
        return matcher.find() ? TextNormalizer.normalize(matcher.group("value")) : "";
    }

    private List<String> extractRelaxedSuggestedReplies(String text) {
        Matcher matcher = REPLIES_PATTERN.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            return List.of();
        }
        Matcher replyMatcher = Pattern.compile("\"((?:\\\\.|[^\"\\\\]|[\\r\\n])*)\"").matcher(matcher.group("value"));
        List<String> replies = new java.util.ArrayList<>();
        while (replyMatcher.find()) {
            replies.add(TextNormalizer.normalize(replyMatcher.group(1)));
        }
        return List.copyOf(replies);
    }
}
