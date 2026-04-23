package com.telegram.codex.conversation.infrastructure.session;

import com.telegram.codex.conversation.application.port.out.ChatSessionPort;
import com.telegram.codex.conversation.domain.session.ChatSessionRecord;
import com.telegram.codex.conversation.infrastructure.persistence.ChatSessionEntity;
import com.telegram.codex.conversation.infrastructure.persistence.ChatSessionJpaRepository;
import com.telegram.codex.shared.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
public class ChatSessionRepository implements ChatSessionPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatSessionRepository.class);

    private final AppProperties properties;
    private final ChatSessionJpaRepository repository;

    public ChatSessionRepository(AppProperties properties, ChatSessionJpaRepository repository) {
        this.properties = properties;
        this.repository = repository;
    }

    @Override
    public Optional<ChatSessionRecord> findActive(String chatId) {
        Optional<ChatSessionEntity> maybeSession = repository.findById(chatId);
        if (maybeSession.isEmpty()) {
            return Optional.empty();
        }
        ChatSessionEntity entity = maybeSession.get();
        if (currentTimeMs() - entity.getUpdatedAt() > sessionTtlMs()) {
            repository.deleteById(chatId);
            LOGGER.info("Reset expired session chat_id={}", chatId);
            return Optional.empty();
        }
        return Optional.of(toRecord(entity));
    }

    @Override
    public void persist(String chatId, String conversationState) {
        ChatSessionEntity entity = repository.findById(chatId).orElseGet(ChatSessionEntity::new);
        entity.setChatId(chatId);
        entity.setLastResponseId(conversationState);
        entity.setUpdatedAt(currentTimeMs());
        repository.save(entity);
    }

    @Override
    public void reset(String chatId) {
        repository.deleteById(chatId);
        LOGGER.info("Reset chat session chat_id={}", chatId);
    }

    private long sessionTtlMs() {
        return Duration.ofDays(properties.getSessionTtlDays()).toMillis();
    }

    private long currentTimeMs() {
        return System.currentTimeMillis();
    }

    private ChatSessionRecord toRecord(ChatSessionEntity entity) {
        return new ChatSessionRecord(entity.getChatId(), entity.getLastResponseId(), entity.getUpdatedAt());
    }
}
