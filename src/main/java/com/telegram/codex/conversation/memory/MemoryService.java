package com.telegram.codex.conversation.memory;

import com.telegram.codex.conversation.ConversationTimeFormatter;

import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class MemoryService {

    private final ChatMemoryRepository chatMemoryRepository;

    public MemoryService(ChatMemoryRepository chatMemoryRepository) {
        this.chatMemoryRepository = chatMemoryRepository;
    }

    public MemorySnapshot snapshot(String chatId) {
        Optional<ChatMemoryRecord> maybeMemory = chatMemoryRepository.find(chatId);
        if (maybeMemory.isEmpty() || maybeMemory.get().memoryText() == null || maybeMemory.get().memoryText().isBlank()) {
            return MemorySnapshot.inactive();
        }
        ChatMemoryRecord memory = maybeMemory.get();
        return MemorySnapshot.active(
            memory.memoryText(),
            ConversationTimeFormatter.format(memory.updatedAt())
        );
    }

    public void reset(String chatId) {
        chatMemoryRepository.reset(chatId);
    }
}
