package com.telegram.codex.conversation.application.port.out;

import com.telegram.codex.conversation.domain.session.ChatSessionRecord;

import java.util.Optional;

public interface ChatSessionPort {

    Optional<ChatSessionRecord> findActive(String chatId);

    void persist(String chatId, String conversationState);

    void reset(String chatId);
}
