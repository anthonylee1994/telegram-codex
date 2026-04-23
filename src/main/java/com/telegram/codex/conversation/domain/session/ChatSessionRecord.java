package com.telegram.codex.conversation.domain.session;

public record ChatSessionRecord(String chatId, String lastResponseId, long updatedAt) {
}
