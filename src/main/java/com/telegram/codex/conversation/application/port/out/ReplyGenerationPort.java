package com.telegram.codex.conversation.application.port.out;

import com.telegram.codex.conversation.application.reply.ReplyResult;

import java.nio.file.Path;
import java.util.List;

public interface ReplyGenerationPort {

    ReplyResult generateReply(
        String text,
        String conversationState,
        List<Path> imageFilePaths,
        String replyToText,
        String longTermMemory
    );
}
