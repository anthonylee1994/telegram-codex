package com.telegram.codex.integration.telegram.infrastructure;

import com.telegram.codex.integration.codex.ReplyParser;
import com.telegram.codex.integration.telegram.domain.TelegramConstants;
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
    private static final Pattern SPOILER_PATTERN = Pattern.compile("\\|\\|([^|\\n]+)\\|\\|");
    private static final Pattern STRIKETHROUGH_PATTERN = Pattern.compile("~~([^~\\n]+)~~");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*([^*\\n]+)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<!_)__([^_\\n]+)__(?!_)");

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
            formatted.append(escapeHtml(stripSingleLeadingNewline(matcher.group(1))));
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
            if (reply == null || reply.isBlank()) {
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
            formatted.append(formatBold(text.substring(cursor, matcher.start())));
            formatted.append("<code>");
            formatted.append(escapeHtml(matcher.group(1)));
            formatted.append("</code>");
            cursor = matcher.end();
        }
        formatted.append(formatBold(text.substring(cursor)));
        return formatted.toString();
    }

    private static String formatBold(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder formatted = new StringBuilder();
        Matcher matcher = BOLD_PATTERN.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            formatted.append(formatSpoiler(text.substring(cursor, matcher.start())));
            formatted.append("<b>");
            formatted.append(escapeHtml(matcher.group(1)));
            formatted.append("</b>");
            cursor = matcher.end();
        }
        formatted.append(formatSpoiler(text.substring(cursor)));
        return formatted.toString();
    }

    private static String formatSpoiler(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder formatted = new StringBuilder();
        Matcher matcher = SPOILER_PATTERN.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            formatted.append(formatStrikethrough(text.substring(cursor, matcher.start())));
            formatted.append("<tg-spoiler>");
            formatted.append(escapeHtml(matcher.group(1)));
            formatted.append("</tg-spoiler>");
            cursor = matcher.end();
        }
        formatted.append(formatStrikethrough(text.substring(cursor)));
        return formatted.toString();
    }

    private static String formatStrikethrough(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder formatted = new StringBuilder();
        Matcher matcher = STRIKETHROUGH_PATTERN.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            formatted.append(formatItalic(text.substring(cursor, matcher.start())));
            formatted.append("<s>");
            formatted.append(escapeHtml(matcher.group(1)));
            formatted.append("</s>");
            cursor = matcher.end();
        }
        formatted.append(formatItalic(text.substring(cursor)));
        return formatted.toString();
    }

    private static String formatItalic(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder formatted = new StringBuilder();
        Matcher matcher = ITALIC_PATTERN.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            String content = matcher.group(1);
            formatted.append(escapeHtml(text.substring(cursor, matcher.start())));
            if (shouldFormatItalic(matcher.group(), content)) {
                formatted.append("<i>");
                formatted.append(escapeHtml(content));
                formatted.append("</i>");
            } else {
                formatted.append(escapeHtml(matcher.group()));
            }
            cursor = matcher.end();
        }
        formatted.append(escapeHtml(text.substring(cursor)));
        return formatted.toString();
    }

    private static boolean shouldFormatItalic(String marker, String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        if (marker != null && marker.startsWith("__") && marker.endsWith("__")) {
            return true;
        }
        for (int i = 0; i < content.length(); i++) {
            if (Character.isLetter(content.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
