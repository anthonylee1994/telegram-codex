package com.telegram.codex.codex;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.constants.MessageConstants;
import com.telegram.codex.constants.TelegramConstants;
import com.telegram.codex.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SuggestedRepliesExtractor {

    private final ObjectMapper objectMapper;

    public SuggestedRepliesExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> extractSuggestedReplies(Object payload, String rawReply) {
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
        if (StringUtils.isNullOrBlank(rawSuggestedReplies)) {
            return List.of();
        }
        try {
            List<?> payload = objectMapper.readValue(rawSuggestedReplies, new TypeReference<>() {
            });
            return sanitizeSuggestedReplies(payload, List.of());
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
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
}
