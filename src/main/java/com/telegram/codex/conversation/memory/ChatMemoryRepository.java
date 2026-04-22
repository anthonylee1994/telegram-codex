package com.telegram.codex.conversation.memory;

import com.telegram.codex.persistence.ChatMemoryEntity;
import com.telegram.codex.persistence.ChatMemoryJpaRepository;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class ChatMemoryRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatMemoryRepository.class);

    private final ChatMemoryJpaRepository repository;

    public ChatMemoryRepository(ChatMemoryJpaRepository repository) {
        this.repository = repository;
    }

    public Optional<ChatMemoryRecord> find(String chatId) {
        return repository.findById(chatId).map(entity -> new ChatMemoryRecord(entity.getChatId(), entity.getMemoryText(), entity.getUpdatedAt()));
    }

    public void persist(String chatId, String memoryText) {
        String normalized = memoryText == null ? "" : memoryText.trim();
        if (normalized.isEmpty()) {
            reset(chatId);
            return;
        }
        ChatMemoryEntity entity = repository.findById(chatId).orElseGet(ChatMemoryEntity::new);
        entity.setChatId(chatId);
        entity.setMemoryText(normalized);
        entity.setUpdatedAt(System.currentTimeMillis());
        repository.save(entity);
    }

    public void reset(String chatId) {
        repository.deleteById(chatId);
        LOGGER.info("Reset chat memory chat_id={}", chatId);
    }
}
