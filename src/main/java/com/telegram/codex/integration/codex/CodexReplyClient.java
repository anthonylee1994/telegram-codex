package com.telegram.codex.integration.codex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.conversation.application.port.out.ReplyGenerationPort;
import com.telegram.codex.conversation.application.reply.ReplyResult;
import com.telegram.codex.conversation.domain.session.Transcript;
import com.telegram.codex.integration.telegram.domain.TelegramConstants;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Component
public class CodexReplyClient implements ReplyGenerationPort {

    private final ExecRunner execRunner;
    private final ObjectMapper objectMapper;
    private final PromptBuilder promptBuilder;
    private final ReplyParser replyParser;
    private final UserMessageBuilder userMessageBuilder;

    public CodexReplyClient(ExecRunner execRunner, ObjectMapper objectMapper, PromptBuilder promptBuilder, ReplyParser replyParser, UserMessageBuilder userMessageBuilder) {
        this.execRunner = execRunner;
        this.objectMapper = objectMapper;
        this.promptBuilder = promptBuilder;
        this.replyParser = replyParser;
        this.userMessageBuilder = userMessageBuilder;
    }

    @Override
    public ReplyResult generateReply(
        String text,
        String conversationState,
        List<Path> imageFilePaths,
        String replyToText,
        String longTermMemory
    ) {
        Transcript transcript = Transcript.fromConversationState(conversationState, objectMapper);
        String userMessage = userMessageBuilder.buildUserMessage(text, imageFilePaths, replyToText);
        Transcript nextTranscript = transcript.append("user", userMessage);
        String systemPrompt = promptBuilder.buildReplySystemPrompt();
        String userPrompt = promptBuilder.buildReplyUserPrompt(
            nextTranscript,
            !imageFilePaths.isEmpty(),
            imageFilePaths.size(),
            longTermMemory
        );
        String rawReply = execRunner.run(systemPrompt, userPrompt, imageFilePaths, replyOutputSchema());
        ReplyParser.ParsedReply parsedReply = replyParser.parseReply(rawReply);
        Transcript updatedTranscript = nextTranscript.append("assistant", parsedReply.text());
        return new ReplyResult(updatedTranscript.toConversationState(objectMapper), parsedReply.suggestedReplies(), parsedReply.text());
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
}
