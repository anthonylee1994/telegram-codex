package com.telegram.codex.conversation.memory;

import com.telegram.codex.conversation.ConversationTimeFormatter;
import com.telegram.codex.util.StringUtils;
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
        if (StringUtils.isNullOrBlank(memory.memoryText())) {
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
}
