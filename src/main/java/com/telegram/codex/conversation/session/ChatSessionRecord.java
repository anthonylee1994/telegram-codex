package com.telegram.codex.conversation.session;

public record ChatSessionRecord(String chatId, String lastResponseId, long updatedAt) {
}
