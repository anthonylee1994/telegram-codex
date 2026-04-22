package com.telegram.codex.codex;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.constants.MessageConstants;
import com.telegram.codex.constants.TelegramConstants;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ReplyParser {

    private static final Pattern TEXT_PATTERN = Pattern.compile("\"text\"\\s*:\\s*\"(?<value>[\\s\\S]*?)\"\\s*,\\s*\"suggested_replies\"\\s*:");
    private static final Pattern REPLIES_PATTERN = Pattern.compile("\"suggested_replies\"\\s*:\\s*\\[(?<value>[\\s\\S]*?)\\]");
    private static final Pattern REPLY_ITEM_PATTERN = Pattern.compile("\"((?:\\\\.|[^\"\\\\]|[\\r\\n])*)\"");

    private final ObjectMapper objectMapper;

    public ReplyParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ParsedReply parseReply(String rawReply) {
        try {
            Object payload = parsePayload(rawReply);
            return new ParsedReply(extractReplyText(payload, rawReply), extractSuggestedReplies(payload, rawReply));
        } catch (Exception error) {
            return new ParsedReply(fallbackReplyText(rawReply), sanitizeSuggestedReplies(List.of(rawReply), MessageConstants.DEFAULT_SUGGESTED_REPLIES));
        }
    }

    private Object parsePayload(String rawReply) throws Exception {
        for (String candidate : candidatePayloads(rawReply)) {
            if (candidate == null || candidate.isBlank()) {
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
        return List.of(normalized, unwrapped, extracted, relaxed);
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
        if ((replyText == null || replyText.isBlank()) && suggestedReplies.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(Map.of("text", replyText == null ? "" : replyText, "suggested_replies", suggestedReplies));
        } catch (Exception error) {
            throw new IllegalStateException("Failed to build relaxed payload", error);
        }
    }

    private String extractRelaxedText(String text) {
        Matcher matcher = TEXT_PATTERN.matcher(text == null ? "" : text);
        return matcher.find() ? normalizeReplyText(matcher.group("value")) : "";
    }

    private List<String> extractRelaxedSuggestedReplies(String text) {
        Matcher matcher = REPLIES_PATTERN.matcher(text == null ? "" : text);
        if (!matcher.find()) {
            return List.of();
        }
        Matcher replyMatcher = REPLY_ITEM_PATTERN.matcher(matcher.group("value"));
        ArrayList<String> replies = new ArrayList<>();
        while (replyMatcher.find()) {
            replies.add(normalizeReplyText(replyMatcher.group(1)));
        }
        return List.copyOf(replies);
    }

    private String extractReplyText(Object payload, String rawReply) {
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

    private List<String> extractSuggestedReplies(Object payload, String rawReply) {
        if (payload instanceof List<?> list) {
            return sanitizeSuggestedReplies(list, MessageConstants.DEFAULT_SUGGESTED_REPLIES);
        }
        if (payload instanceof Map<?, ?> map) {
            Object suggestedReplies = map.get("suggested_replies");
            return sanitizeSuggestedReplies(suggestedReplies instanceof List<?> list ? list : List.of(), MessageConstants.DEFAULT_SUGGESTED_REPLIES);
        }
        return sanitizeSuggestedReplies(List.of(rawReply), MessageConstants.DEFAULT_SUGGESTED_REPLIES);
    }

    public List<String> parseSuggestedReplies(String rawSuggestedReplies) {
        if (rawSuggestedReplies == null || rawSuggestedReplies.isBlank()) {
            return List.of();
        }
        try {
            List<?> payload = objectMapper.readValue(rawSuggestedReplies, new TypeReference<>() {
            });
            return sanitizeSuggestedReplies(payload, List.of());
        } catch (Exception error) {
            return List.of();
        }
    }

    public List<String> sanitizeSuggestedReplies(List<?> replies, List<String> fallback) {
        ArrayList<String> cleaned = new ArrayList<>();
        for (Object reply : replies) {
            if (!(reply instanceof String stringReply)) {
                continue;
            }
            String normalized = stringReply.trim().replaceAll("\\s+", " ");
            if (!normalized.isBlank() && !cleaned.contains(normalized)) {
                cleaned.add(normalized.length() > TelegramConstants.MAX_SUGGESTED_REPLY_LENGTH ? normalized.substring(0, TelegramConstants.MAX_SUGGESTED_REPLY_LENGTH) : normalized);
            }
            if (cleaned.size() == TelegramConstants.MAX_SUGGESTED_REPLIES) {
                break;
            }
        }
        if (cleaned.size() < TelegramConstants.MAX_SUGGESTED_REPLIES) {
            return fallback;
        }
        return List.copyOf(cleaned);
    }

    private String fallbackReplyText(String rawReply) {
        String normalized = normalizeReplyText(rawReply == null ? "" : rawReply);
        if (normalized.isBlank()) {
            throw new IllegalStateException("codex exec returned an empty reply");
        }
        return normalized;
    }

    private String normalizeReplyText(String value) {
        return value.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\t", "\t").trim();
    }

    public record ParsedReply(String text, List<String> suggestedReplies) {
    }
}
