package com.telegram.codex.telegram;

import com.telegram.codex.codex.ReplyParser;
import com.telegram.codex.constants.TelegramConstants;
import com.telegram.codex.util.HtmlEscaper;
import com.telegram.codex.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TelegramMessageFormatter {

    private static final Pattern FENCED_CODE_BLOCK_PATTERN = Pattern.compile("```(?:[\\t ]*[\\w#+.-]+)?\\n?(.*?)```", Pattern.DOTALL);
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`\\n]+)`");

    private final ReplyParser replyParser;

    public TelegramMessageFormatter(ReplyParser replyParser) {
        this.replyParser = replyParser;
    }

    public String formatForTelegram(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder formatted = new StringBuilder();
        Matcher matcher = FENCED_CODE_BLOCK_PATTERN.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            formatted.append(formatInlineCode(text.substring(cursor, matcher.start())));
            formatted.append("<pre><code>");
            formatted.append(HtmlEscaper.escape(stripSingleLeadingNewline(matcher.group(1))));
            formatted.append("</code></pre>");
            cursor = matcher.end();
        }
        formatted.append(formatInlineCode(text.substring(cursor)));
        return formatted.toString();
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

    private static String stripSingleLeadingNewline(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.startsWith("\r\n")) {
            return text.substring(2);
        }
        if (text.startsWith("\n")) {
            return text.substring(1);
        }
        return text;
    }

    private static String formatInlineCode(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder formatted = new StringBuilder();
        Matcher matcher = INLINE_CODE_PATTERN.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            formatted.append(HtmlEscaper.escape(text.substring(cursor, matcher.start())));
            formatted.append("<code>");
            formatted.append(HtmlEscaper.escape(matcher.group(1)));
            formatted.append("</code>");
            cursor = matcher.end();
        }
        formatted.append(HtmlEscaper.escape(text.substring(cursor)));
        return formatted.toString();
    }
}
