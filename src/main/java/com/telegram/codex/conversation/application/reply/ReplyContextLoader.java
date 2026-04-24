package com.telegram.codex.conversation.application.reply;

import com.telegram.codex.conversation.domain.memory.ChatMemoryRecord;
import com.telegram.codex.conversation.domain.session.ChatSessionRecord;
import com.telegram.codex.conversation.infrastructure.memory.ChatMemoryRepository;
import com.telegram.codex.conversation.infrastructure.session.ChatSessionRepository;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.springframework.stereotype.Component;

@Component
public class ReplyContextLoader {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMemoryRepository chatMemoryRepository;

    public ReplyContextLoader(ChatSessionRepository chatSessionRepository, ChatMemoryRepository chatMemoryRepository) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMemoryRepository = chatMemoryRepository;
    }

    public ReplyContextSnapshot load(InboundMessage message) {
        String promptText = message.text() == null ? "" : message.text();
        String lastResponseId = chatSessionRepository.findActive(message.chatId())
            .map(ChatSessionRecord::lastResponseId)
            .orElse(null);
        String memoryText = chatMemoryRepository.find(message.chatId())
            .map(ChatMemoryRecord::memoryText)
            .orElse(null);
        return new ReplyContextSnapshot(promptText, lastResponseId, message.replyToText(), memoryText);
    }
}
