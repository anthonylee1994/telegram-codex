package com.telegram.codex.integration.codex;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.conversation.application.gateway.ReplyGenerationGateway;
import com.telegram.codex.conversation.application.reply.ReplyResult;
import com.telegram.codex.conversation.domain.session.Transcript;
import com.telegram.codex.integration.codex.schema.CodexOutputSchema;
import com.telegram.codex.integration.telegram.domain.TelegramConstants;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
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

    private CodexOutputSchema replyOutputSchema() {
        return new ReplyOutputSchema(
            "object",
            false,
            List.of("text", "suggested_replies"),
            new ReplyProperties(
                new StringPropertySchema("string", 1),
                new SuggestedRepliesSchema(
                    "array",
                    TelegramConstants.MAX_SUGGESTED_REPLIES,
                    TelegramConstants.MAX_SUGGESTED_REPLIES,
                    new StringPropertySchema("string", 1)
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
        // 呢度係寫返「今次 user turn」入 transcript；真正送去模型嘅 prompt 之後再由 PromptBuilder 組。
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

    private record ReplyOutputSchema(
        @JsonProperty("type") String type,
        @JsonProperty("additionalProperties") boolean additionalProperties,
        @JsonProperty("required") List<String> required,
        @JsonProperty("properties") ReplyProperties properties
    ) implements CodexOutputSchema {
    }

    private record ReplyProperties(
        @JsonProperty("text") StringPropertySchema text,
        @JsonProperty("suggested_replies") SuggestedRepliesSchema suggestedReplies
    ) {
    }

    private record StringPropertySchema(
        @JsonProperty("type") String type,
        @JsonProperty("minLength") Integer minLength
    ) {
    }

    private record SuggestedRepliesSchema(
        @JsonProperty("type") String type,
        @JsonProperty("minItems") int minItems,
        @JsonProperty("maxItems") int maxItems,
        @JsonProperty("items") StringPropertySchema items
    ) {
    }
}
