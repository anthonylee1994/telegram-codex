package com.telegramcodex.codex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegramcodex.conversation.ReplyResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CliClient {

    private static final int MAX_SUGGESTED_REPLIES = 3;

    private final ExecRunner execRunner;
    private final ObjectMapper objectMapper;
    private final PromptBuilder promptBuilder;
    private final ReplyParser replyParser;

    public CliClient(ExecRunner execRunner, ObjectMapper objectMapper, PromptBuilder promptBuilder, ReplyParser replyParser) {
        this.execRunner = execRunner;
        this.objectMapper = objectMapper;
        this.promptBuilder = promptBuilder;
        this.replyParser = replyParser;
    }

    public ReplyResult generateReply(
        String text,
        String conversationState,
        List<Path> imageFilePaths,
        String replyToText,
        String longTermMemory
    ) {
        Transcript transcript = Transcript.fromConversationState(conversationState, objectMapper);
        String userMessage = buildUserMessage(text, imageFilePaths, replyToText);
        Transcript nextTranscript = transcript.append("user", userMessage);
        String prompt = promptBuilder.buildReplyPrompt(
            nextTranscript,
            !imageFilePaths.isEmpty(),
            imageFilePaths.size(),
            longTermMemory
        );
        String rawReply = execRunner.run(prompt, imageFilePaths, replyOutputSchema());
        ReplyParser.ParsedReply parsedReply = replyParser.parseReply(rawReply);
        Transcript updatedTranscript = nextTranscript.append("assistant", parsedReply.text());
        return new ReplyResult(updatedTranscript.toConversationState(objectMapper), parsedReply.suggestedReplies(), parsedReply.text());
    }

    private String buildUserMessage(String text, List<Path> imageFilePaths, String replyToText) {
        String baseText = text == null ? "" : text;
        if (baseText.isBlank() && !imageFilePaths.isEmpty()) {
            baseText = buildUnpromptedImageMessage(imageFilePaths.size());
        }
        if (replyToText == null || replyToText.isBlank()) {
            return baseText;
        }
        return String.join("\n",
            "你而家係回覆緊之前一則訊息。",
            "被引用訊息：" + replyToText,
            "你今次嘅新訊息：" + (baseText.isBlank() ? "（冇文字）" : baseText)
        );
    }

    private String buildUnpromptedImageMessage(int imageCount) {
        if (imageCount == 1) {
            return "我上載咗 1 張圖。請先描述圖 1，再按內容幫我分析重點。";
        }
        String labels = String.join("、", java.util.stream.IntStream.rangeClosed(1, imageCount).mapToObj(index -> "圖 " + index).toList());
        return "我上載咗 " + imageCount + " 張圖。請按 " + labels + " 逐張描述，再比較異同同整理重點。";
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
                    "minItems", MAX_SUGGESTED_REPLIES,
                    "maxItems", MAX_SUGGESTED_REPLIES,
                    "items", Map.of("type", "string", "minLength", 1)
                )
            )
        );
    }
}
