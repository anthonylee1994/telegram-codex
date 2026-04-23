package com.telegram.codex.conversation.application.port.out;

import com.telegram.codex.conversation.domain.memory.ChatMemoryRecord;

import java.util.Optional;

public interface ChatMemoryPort {

    Optional<ChatMemoryRecord> find(String chatId);

    void persist(String chatId, String memoryText);

    void reset(String chatId);
}
