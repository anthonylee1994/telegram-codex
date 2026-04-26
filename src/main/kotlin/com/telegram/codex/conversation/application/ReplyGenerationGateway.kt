package com.telegram.codex.conversation.application

import java.nio.file.Path

interface ReplyGenerationGateway {
    fun generateReply(
        userMessage: String?,
        conversationState: String?,
        imageFilePaths: List<Path>,
        replyToText: String?,
        longTermMemory: String?,
    ): ReplyResult
}
