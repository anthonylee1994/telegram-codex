package com.telegram.codex.telegram;

import com.telegram.codex.codex.ReplyParser;
import com.telegram.codex.constants.TelegramConstants;
import com.telegram.codex.util.HtmlEscaper;
import com.telegram.codex.util.StringUtils;
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
        return HtmlEscaper.escape(text);
    }

    public Map<String, Object> buildReplyMarkup(List<String> suggestedReplies, boolean removeKeyboard) {
        if (removeKeyboard) {
            return Map.of("remove_keyboard", true);
        }
        List<String> cleanedReplies = cleanReplies(suggestedReplies);
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

    private List<String> cleanReplies(List<String> replies) {
        ArrayList<String> cleaned = new ArrayList<>();
        for (String reply : replies) {
            if (StringUtils.isNullOrBlank(reply)) {
                continue;
            }
            String normalized = reply.trim();
            if (!cleaned.contains(normalized)) {
                cleaned.add(normalized);
            }
            if (cleaned.size() == TelegramConstants.MAX_SUGGESTED_REPLIES) {
                break;
            }
        }
        return List.copyOf(cleaned);
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
