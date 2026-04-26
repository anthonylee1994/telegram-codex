package com.telegram.codex.conversation.application.gateway

import com.telegram.codex.conversation.application.reply.ReplyResult
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
