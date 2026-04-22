package com.telegramcodex.conversation;

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
