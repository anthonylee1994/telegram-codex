package com.telegram.codex.conversation.domain

data class ProcessedUpdateRecord(
    val updateId: Long,
    val chatId: String?,
    val messageId: Long,
    val processedAt: Long,
    val replyText: String?,
    val conversationState: String?,
    val suggestedReplies: String?,
    val sentAt: Long?,
)
