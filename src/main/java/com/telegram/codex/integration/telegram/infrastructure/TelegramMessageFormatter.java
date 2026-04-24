package com.telegram.codex.integration.telegram.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.telegram.codex.integration.codex.ReplyParser;
import com.telegram.codex.integration.telegram.domain.TelegramConstants;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
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
    private static final List<InlineRule> INLINE_RULES = List.of(
        new InlineRule(INLINE_CODE_PATTERN, match -> wrap("code", match.group(1))),
        new InlineRule(BOLD_PATTERN, match -> wrap("b", match.group(1))),
        new InlineRule(SPOILER_PATTERN, match -> wrap("tg-spoiler", match.group(1))),
        new InlineRule(STRIKETHROUGH_PATTERN, match -> wrap("s", match.group(1))),
        new InlineRule(ITALIC_PATTERN, TelegramMessageFormatter::formatItalicMatch)
    );

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
            formatted.append(formatInlineSegment(text.substring(cursor, matcher.start())));
            formatted.append(renderCodeBlock(matcher.group(1)));
            cursor = matcher.end();
        }
        formatted.append(formatInlineSegment(text.substring(cursor)));
        return formatted.toString();
    }

    public TelegramReplyMarkup buildReplyMarkup(List<String> suggestedReplies, boolean removeKeyboard) {
        if (removeKeyboard) {
            return new RemoveKeyboardMarkup(true);
        }
        List<String> cleanedReplies = cleanReplies(suggestedReplies);
        if (cleanedReplies.isEmpty()) {
            return null;
        }
        List<List<KeyboardButton>> keyboard = cleanedReplies.stream()
            .map(reply -> List.of(new KeyboardButton(reply)))
            .toList();
        return new ReplyKeyboardMarkup(
            keyboard,
            true,
            true
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

    private static String renderCodeBlock(String text) {
        return "<pre><code>" + escapeHtml(stripSingleLeadingNewline(text)) + "</code></pre>";
    }

    private static String formatInlineSegment(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return applyRules(text, 0);
    }

    private static String applyRules(String text, int ruleIndex) {
        if (ruleIndex >= INLINE_RULES.size()) {
            return escapeHtml(text);
        }
        return applyRule(text, INLINE_RULES.get(ruleIndex), ruleIndex);
    }

    private static String applyRule(String text, InlineRule rule, int ruleIndex) {
        StringBuilder formatted = new StringBuilder();
        Matcher matcher = rule.pattern().matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            formatted.append(applyRules(text.substring(cursor, matcher.start()), ruleIndex + 1));
            formatted.append(rule.replacement().apply(matcher));
            cursor = matcher.end();
        }
        formatted.append(applyRules(text.substring(cursor), ruleIndex + 1));
        return formatted.toString();
    }

    private static String formatItalicMatch(Matcher matcher) {
        String marker = matcher.group();
        String content = matcher.group(1);
        if (!shouldFormatItalic(marker, content)) {
            return marker;
        }
        return wrap("i", content);
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

    private static String wrap(String tag, String content) {
        return "<" + tag + ">" + escapeHtml(content) + "</" + tag + ">";
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

    private record InlineRule(Pattern pattern, Function<Matcher, String> replacement) {
    }

    public sealed interface TelegramReplyMarkup permits RemoveKeyboardMarkup, ReplyKeyboardMarkup {
    }

    public record RemoveKeyboardMarkup(
        @JsonProperty("remove_keyboard") boolean removeKeyboard
    ) implements TelegramReplyMarkup {
    }

    public record ReplyKeyboardMarkup(
        @JsonProperty("keyboard") List<List<KeyboardButton>> keyboard,
        @JsonProperty("resize_keyboard") boolean resizeKeyboard,
        @JsonProperty("one_time_keyboard") boolean oneTimeKeyboard
    ) implements TelegramReplyMarkup {
    }

    public record KeyboardButton(
        @JsonProperty("text") String text
    ) {
    }
}
