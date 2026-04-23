package com.telegram.codex.conversation.infrastructure.memory;

import com.telegram.codex.conversation.application.port.out.ChatMemoryPort;
import com.telegram.codex.conversation.domain.memory.ChatMemoryRecord;
import com.telegram.codex.conversation.infrastructure.persistence.ChatMemoryEntity;
import com.telegram.codex.conversation.infrastructure.persistence.ChatMemoryJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ChatMemoryRepository implements ChatMemoryPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatMemoryRepository.class);

    private final ChatMemoryJpaRepository repository;

    public ChatMemoryRepository(ChatMemoryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ChatMemoryRecord> find(String chatId) {
        return repository.findById(chatId).map(entity -> new ChatMemoryRecord(entity.getChatId(), entity.getMemoryText(), entity.getUpdatedAt()));
    }

    @Override
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

    @Override
    public void reset(String chatId) {
        repository.deleteById(chatId);
        LOGGER.info("Reset chat memory chat_id={}", chatId);
    }
}
