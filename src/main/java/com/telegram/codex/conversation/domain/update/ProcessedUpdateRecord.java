package com.telegram.codex.conversation.domain.update;

public record ProcessedUpdateRecord(
    long updateId,
    String chatId,
    long messageId,
    long processedAt,
    String replyText,
    String conversationState,
    String suggestedReplies,
    Long sentAt
) {
}
