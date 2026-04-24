package com.telegram.codex.conversation.application.reply;

import com.telegram.codex.conversation.application.gateway.MemoryMergeGateway;
import com.telegram.codex.conversation.domain.memory.ChatMemoryRecord;
import com.telegram.codex.conversation.infrastructure.memory.ChatMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LongTermMemoryRefresher {

    private static final Logger LOGGER = LoggerFactory.getLogger(LongTermMemoryRefresher.class);

    private final ChatMemoryRepository chatMemoryRepository;
    private final MemoryMergeGateway memoryClient;

    public LongTermMemoryRefresher(ChatMemoryRepository chatMemoryRepository, MemoryMergeGateway memoryClient) {
        this.chatMemoryRepository = chatMemoryRepository;
        this.memoryClient = memoryClient;
    }

    public void refresh(String chatId, String userMessage, String assistantReply) {
        if (userMessage == null || userMessage.isBlank()) {
            return;
        }
        try {
            persist(chatId, userMessage, assistantReply);
        } catch (Exception error) {
            LOGGER.warn("Failed to refresh long-term memory chat_id={} error={}", chatId, error.getMessage());
        }
    }

    void persist(String chatId, String userMessage, String assistantReply) {
        String existingMemory = chatMemoryRepository.find(chatId).map(ChatMemoryRecord::memoryText).orElse("");
        String mergedMemory = memoryClient.merge(existingMemory, userMessage, assistantReply);
        if (!mergedMemory.equals(existingMemory)) {
            chatMemoryRepository.persist(chatId, mergedMemory);
        }
    }
}
