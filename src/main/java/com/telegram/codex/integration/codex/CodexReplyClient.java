package com.telegram.codex.integration.codex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.conversation.application.gateway.ReplyGenerationGateway;
import com.telegram.codex.conversation.application.reply.ReplyResult;
import com.telegram.codex.conversation.domain.session.Transcript;
import com.telegram.codex.integration.telegram.domain.TelegramConstants;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Component
public class CodexReplyClient implements ReplyGenerationGateway {

    private final ExecRunner execRunner;
    private final ObjectMapper objectMapper;
    private final PromptBuilder promptBuilder;
    private final ReplyParser replyParser;

    public CodexReplyClient(ExecRunner execRunner, ObjectMapper objectMapper, PromptBuilder promptBuilder, ReplyParser replyParser) {
        this.execRunner = execRunner;
        this.objectMapper = objectMapper;
        this.promptBuilder = promptBuilder;
        this.replyParser = replyParser;
    }

    @Override
    public ReplyResult generateReply(
        String text,
        String conversationState,
        List<Path> imageFilePaths,
        String replyToText,
        String longTermMemory
    ) {
        Transcript nextTranscript = appendUserMessage(conversationState, text, imageFilePaths, replyToText);
        String rawReply = execRunner.run(
            promptBuilder.buildReplySystemPrompt(),
            buildReplyPrompt(nextTranscript, imageFilePaths, longTermMemory),
            imageFilePaths,
            replyOutputSchema()
        );
        return buildReplyResult(nextTranscript, rawReply);
    }

    private Map<String, Object> replyOutputSchema() {
        return Map.of(
            "type", "object",
            "additionalProperties", false,
            "required", List.of("text", "suggested_replies"),
            "properties", Map.of(
                "text", Map.of("type", "string", "minLength", 1),
                "suggested_replies", Map.of(
                    "type", "array",
                    "minItems", TelegramConstants.MAX_SUGGESTED_REPLIES,
                    "maxItems", TelegramConstants.MAX_SUGGESTED_REPLIES,
                    "items", Map.of("type", "string", "minLength", 1)
                )
            )
        );
    }

    private String buildUserMessage(String text, List<Path> imageFilePaths, String replyToText) {
        String baseText = normalizeUserText(text, imageFilePaths);
        if (replyToText == null || replyToText.isBlank()) {
            return baseText;
        }
        return String.join("\n",
            "你而家係回覆緊之前一則訊息。",
            "被引用訊息：" + replyToText,
            "你今次嘅新訊息：" + (baseText.isBlank() ? "（冇文字）" : baseText)
        );
    }

    private Transcript appendUserMessage(
        String conversationState,
        String text,
        List<Path> imageFilePaths,
        String replyToText
    ) {
        Transcript transcript = Transcript.fromConversationState(conversationState, objectMapper);
        return transcript.append("user", buildUserMessage(text, imageFilePaths, replyToText));
    }

    private String buildReplyPrompt(Transcript nextTranscript, List<Path> imageFilePaths, String longTermMemory) {
        return promptBuilder.buildReplyUserPrompt(
            nextTranscript,
            !imageFilePaths.isEmpty(),
            imageFilePaths.size(),
            longTermMemory
        );
    }

    private ReplyResult buildReplyResult(Transcript nextTranscript, String rawReply) {
        ReplyParser.ParsedReply parsedReply = replyParser.parseReply(rawReply);
        Transcript updatedTranscript = nextTranscript.append("assistant", parsedReply.text());
        return new ReplyResult(updatedTranscript.toConversationState(objectMapper), parsedReply.suggestedReplies(), parsedReply.text());
    }

    private String normalizeUserText(String text, List<Path> imageFilePaths) {
        String baseText = text == null ? "" : text;
        if (!baseText.isBlank() || imageFilePaths.isEmpty()) {
            return baseText;
        }
        return buildUnpromptedImageMessage(imageFilePaths.size());
    }

    private String buildUnpromptedImageMessage(int imageCount) {
        if (imageCount == 1) {
            return "我上載咗張圖。請先描述圖片，再按內容幫我分析重點。";
        }
        String labels = String.join("、", IntStream.rangeClosed(1, imageCount).mapToObj(index -> "圖 " + index).toList());
        return "我上載咗 " + imageCount + " 張圖。請按 " + labels + " 逐張描述，再比較異同同整理重點。";
    }
}
