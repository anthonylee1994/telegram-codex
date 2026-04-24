package com.telegram.codex.integration.codex;

import com.fasterxml.jackson.databind.JsonNode;
import com.telegram.codex.conversation.domain.MessageConstants;
import com.telegram.codex.integration.telegram.domain.TelegramConstants;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
public class ReplyParser {

    private final JsonPayloadParser jsonPayloadParser;

    public ReplyParser(JsonPayloadParser jsonPayloadParser) {
        this.jsonPayloadParser = jsonPayloadParser;
    }

    public ParsedReply parseReply(String rawReply) {
        try {
            JsonNode payload = jsonPayloadParser.parsePayload(rawReply);
            String text = extractReplyText(payload, rawReply);
            List<String> suggestedReplies = extractSuggestedReplies(payload, rawReply);
            return new ParsedReply(text, suggestedReplies);
        } catch (Exception error) {
            String fallbackText = fallbackReplyText(rawReply);
            List<String> fallbackReplies = sanitizeSuggestedReplies(
                List.of(rawReply),
                MessageConstants.DEFAULT_SUGGESTED_REPLIES
            );
            return new ParsedReply(fallbackText, fallbackReplies);
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

    private String extractReplyText(JsonNode payload, String rawReply) {
        if (payload != null && payload.isObject()) {
            JsonNode text = payload.get("text");
            if (text != null && text.isTextual() && !text.asText().isBlank()) {
                return TextNormalizer.normalize(text.asText());
            }
            String candidate = findTextCandidate(payload.elements());
            if (candidate != null) {
                return TextNormalizer.normalize(candidate);
            }
        }
        if (payload != null && payload.isTextual() && !payload.asText().isBlank()) {
            return TextNormalizer.normalize(payload.asText());
        }
        return fallbackReplyText(rawReply);
    }

    private String fallbackReplyText(String rawReply) {
        String normalized = TextNormalizer.normalize(rawReply == null ? "" : rawReply);
        if (normalized.isBlank()) {
            throw new IllegalStateException("codex exec returned an empty reply");
        }
        return normalized;
    }

    private List<String> extractSuggestedReplies(JsonNode payload, String rawReply) {
        if (payload != null && payload.isArray()) {
            return sanitizeSuggestedReplies(asTextList(payload.elements()), MessageConstants.DEFAULT_SUGGESTED_REPLIES);
        }
        if (payload != null && payload.isObject()) {
            JsonNode suggestedReplies = payload.get("suggested_replies");
            if (suggestedReplies != null && suggestedReplies.isArray()) {
                return sanitizeSuggestedReplies(asTextList(suggestedReplies.elements()), MessageConstants.DEFAULT_SUGGESTED_REPLIES);
            }
        }
        return sanitizeSuggestedReplies(List.of(rawReply), MessageConstants.DEFAULT_SUGGESTED_REPLIES);
    }

    private String findTextCandidate(Iterator<JsonNode> values) {
        String candidate = null;
        while (values.hasNext()) {
            JsonNode value = values.next();
            if (!value.isTextual()) {
                continue;
            }
            String normalized = value.asText().trim();
            if (normalized.isBlank()) {
                continue;
            }
            if (candidate == null || normalized.compareTo(candidate) > 0) {
                candidate = normalized;
            }
        }
        return candidate;
    }

    private List<String> asTextList(Iterator<JsonNode> values) {
        ArrayList<String> replies = new ArrayList<>();
        while (values.hasNext()) {
            JsonNode value = values.next();
            if (value.isTextual()) {
                replies.add(value.asText());
            }
        }
        return List.copyOf(replies);
    }

    public record ParsedReply(String text, List<String> suggestedReplies) {
    }
}
