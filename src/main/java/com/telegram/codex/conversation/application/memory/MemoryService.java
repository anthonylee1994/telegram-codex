package com.telegram.codex.conversation.application.memory;

import com.telegram.codex.conversation.domain.ConversationTimeFormatter;
import com.telegram.codex.conversation.domain.memory.ChatMemoryRecord;
import com.telegram.codex.conversation.infrastructure.memory.ChatMemoryRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MemoryService {

    private final ChatMemoryRepository chatMemoryRepository;

    public MemoryService(ChatMemoryRepository chatMemoryRepository) {
        this.chatMemoryRepository = chatMemoryRepository;
    }

    public MemorySnapshot snapshot(String chatId) {
        Optional<ChatMemoryRecord> maybeMemory = chatMemoryRepository.find(chatId);
        if (maybeMemory.isEmpty()) {
            return MemorySnapshot.inactive();
        }
        ChatMemoryRecord memory = maybeMemory.get();
        if (memory.memoryText() == null || memory.memoryText().isBlank()) {
            return MemorySnapshot.inactive();
        }
        return MemorySnapshot.active(
            memory.memoryText(),
            ConversationTimeFormatter.format(memory.updatedAt())
        );
    }

    public void reset(String chatId) {
        chatMemoryRepository.reset(chatId);
    }

    public record MemorySnapshot(boolean active, String memoryText, String lastUpdatedAt) {

        public static MemorySnapshot inactive() {
            return new MemorySnapshot(false, null, null);
        }

        public static MemorySnapshot active(String memoryText, String lastUpdatedAt) {
            return new MemorySnapshot(true, memoryText, lastUpdatedAt);
        }
    }
}
