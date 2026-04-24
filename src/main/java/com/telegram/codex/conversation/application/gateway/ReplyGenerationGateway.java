package com.telegram.codex.conversation.application.gateway;

import com.telegram.codex.conversation.application.reply.ReplyResult;

import java.nio.file.Path;
import java.util.List;

public interface ReplyGenerationGateway {

    ReplyResult generateReply(
        String userMessage,
        String conversationState,
        List<Path> imageFilePaths,
        String replyToText,
        String longTermMemory
    );
}
