package com.telegram.codex.conversation.domain.memory

data class ChatMemoryRecord(
    val chatId: String?,
    val memoryText: String?,
    val updatedAt: Long,
)
