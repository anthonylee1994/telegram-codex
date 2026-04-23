package com.telegram.codex.integration.codex;

import com.telegram.codex.conversation.application.port.out.SuggestedRepliesPort;
import com.telegram.codex.conversation.domain.MessageConstants;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReplyParser implements SuggestedRepliesPort {

    private final JsonPayloadParser jsonPayloadParser;
    private final ReplyTextExtractor replyTextExtractor;
    private final SuggestedRepliesExtractor suggestedRepliesExtractor;

    public ReplyParser(
        JsonPayloadParser jsonPayloadParser,
        ReplyTextExtractor replyTextExtractor,
        SuggestedRepliesExtractor suggestedRepliesExtractor
    ) {
        this.jsonPayloadParser = jsonPayloadParser;
        this.replyTextExtractor = replyTextExtractor;
        this.suggestedRepliesExtractor = suggestedRepliesExtractor;
    }

    public ParsedReply parseReply(String rawReply) {
        try {
            Object payload = jsonPayloadParser.parsePayload(rawReply);
            String text = replyTextExtractor.extractReplyText(payload, rawReply);
            List<String> suggestedReplies = suggestedRepliesExtractor.extractSuggestedReplies(payload, rawReply);
            return new ParsedReply(text, suggestedReplies);
        } catch (Exception error) {
            String fallbackText = replyTextExtractor.fallbackReplyText(rawReply);
            List<String> fallbackReplies = suggestedRepliesExtractor.sanitizeSuggestedReplies(
                List.of(rawReply),
                MessageConstants.DEFAULT_SUGGESTED_REPLIES
            );
            return new ParsedReply(fallbackText, fallbackReplies);
        }
    }

    @Override
    public List<String> parseSuggestedReplies(String rawSuggestedReplies) {
        return suggestedRepliesExtractor.parseSuggestedReplies(rawSuggestedReplies);
    }

    public List<String> sanitizeSuggestedReplies(List<?> replies, List<String> fallback) {
        return suggestedRepliesExtractor.sanitizeSuggestedReplies(replies, fallback);
    }

    public record ParsedReply(String text, List<String> suggestedReplies) {
    }
}
