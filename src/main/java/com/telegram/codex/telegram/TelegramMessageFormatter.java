package com.telegram.codex.telegram;

import com.telegram.codex.codex.ReplyParser;
import com.telegram.codex.constants.TelegramConstants;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TelegramMessageFormatter {

    private final ReplyParser replyParser;

    public TelegramMessageFormatter(ReplyParser replyParser) {
        this.replyParser = replyParser;
    }

    public String formatForTelegram(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    public Map<String, Object> buildReplyMarkup(List<String> suggestedReplies, boolean removeKeyboard) {
        if (removeKeyboard) {
            return Map.of("remove_keyboard", true);
        }
        ArrayList<String> cleanedReplies = new ArrayList<>();
        for (String reply : suggestedReplies) {
            if (reply == null || reply.isBlank()) {
                continue;
            }
            String normalized = reply.trim();
            if (!cleanedReplies.contains(normalized)) {
                cleanedReplies.add(normalized);
            }
            if (cleanedReplies.size() == TelegramConstants.MAX_SUGGESTED_REPLIES) {
                break;
            }
        }
        if (cleanedReplies.isEmpty()) {
            return null;
        }
        List<List<Map<String, String>>> keyboard = cleanedReplies.stream()
            .map(reply -> List.of(Map.of("text", reply)))
            .toList();
        return Map.of(
            "keyboard", keyboard,
            "resize_keyboard", true,
            "one_time_keyboard", true
        );
    }

    public NormalizedReply normalizeReply(String text, List<String> suggestedReplies) {
        ReplyParser.ParsedReply payload = replyParser.parseReply(text);
        String normalizedText = payload.text().isBlank() ? text : payload.text();
        List<String> normalizedSuggestedReplies = suggestedReplies == null || suggestedReplies.isEmpty()
            ? payload.suggestedReplies()
            : suggestedReplies;
        return new NormalizedReply(normalizedText, normalizedSuggestedReplies);
    }

    public record NormalizedReply(String text, List<String> suggestedReplies) {
    }
}
