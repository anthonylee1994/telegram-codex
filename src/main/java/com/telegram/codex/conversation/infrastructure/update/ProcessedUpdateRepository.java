package com.telegram.codex.conversation.infrastructure.update;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.conversation.application.port.out.ProcessedUpdatePort;
import com.telegram.codex.conversation.application.reply.ReplyResult;
import com.telegram.codex.conversation.domain.update.ProcessedUpdateRecord;
import com.telegram.codex.conversation.infrastructure.persistence.ProcessedUpdateEntity;
import com.telegram.codex.conversation.infrastructure.persistence.ProcessedUpdateJpaRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
public class ProcessedUpdateRepository implements ProcessedUpdatePort {

    private static final long INFLIGHT_TIMEOUT_MS = Duration.ofMinutes(5).toMillis();

    private final ObjectMapper objectMapper;
    private final ProcessedUpdateJpaRepository repository;

    public ProcessedUpdateRepository(ObjectMapper objectMapper, ProcessedUpdateJpaRepository repository) {
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    @Override
    public Optional<ProcessedUpdateRecord> find(long updateId) {
        return repository.findById(updateId).map(this::toRecord);
    }

    @Transactional
    @Override
    public boolean beginProcessing(long updateId, String chatId, long messageId) {
        long now = System.currentTimeMillis();
        Optional<ProcessedUpdateEntity> existing = repository.findById(updateId);
        if (existing.isEmpty()) {
            ProcessedUpdateEntity entity = new ProcessedUpdateEntity();
            entity.setUpdateId(updateId);
            entity.setChatId(chatId);
            entity.setMessageId(messageId);
            entity.setProcessedAt(now);
            repository.save(entity);
            return true;
        }

        ProcessedUpdateEntity entity = existing.get();
        if (entity.getSentAt() != null) {
            return false;
        }
        if (entity.getReplyText() != null && entity.getConversationState() != null) {
            return false;
        }
        if (now - entity.getProcessedAt() < INFLIGHT_TIMEOUT_MS) {
            return false;
        }

        entity.setChatId(chatId);
        entity.setMessageId(messageId);
        entity.setProcessedAt(now);
        entity.setReplyText(null);
        entity.setConversationState(null);
        entity.setSuggestedReplies(null);
        entity.setSentAt(null);
        repository.save(entity);
        return true;
    }

    @Transactional
    @Override
    public void clearProcessing(long updateId) {
        repository.findById(updateId).ifPresent(entity -> {
            if (entity.getSentAt() == null && entity.getReplyText() == null && entity.getConversationState() == null) {
                repository.delete(entity);
            }
        });
    }

    @Transactional
    @Override
    public void markProcessed(long updateId, String chatId, long messageId) {
        ProcessedUpdateEntity entity = repository.findById(updateId).orElseGet(ProcessedUpdateEntity::new);
        long now = System.currentTimeMillis();
        entity.setUpdateId(updateId);
        entity.setChatId(chatId);
        entity.setMessageId(messageId);
        entity.setProcessedAt(now);
        entity.setSentAt(now);
        repository.save(entity);
    }

    @Transactional
    @Override
    public void savePendingReply(long updateId, String chatId, long messageId, ReplyResult result) {
        ProcessedUpdateEntity entity = repository.findById(updateId).orElseGet(ProcessedUpdateEntity::new);
        entity.setUpdateId(updateId);
        entity.setChatId(chatId);
        entity.setMessageId(messageId);
        entity.setProcessedAt(System.currentTimeMillis());
        entity.setReplyText(result.text());
        entity.setConversationState(result.conversationState());
        entity.setSuggestedReplies(writeSuggestedReplies(result.suggestedReplies()));
        entity.setSentAt(null);
        repository.save(entity);
    }

    @Override
    public long pruneSentBefore(long cutoff) {
        return repository.deleteBySentAtIsNotNullAndProcessedAtLessThan(cutoff);
    }

    private String writeSuggestedReplies(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Failed to persist suggested replies", error);
        }
    }

    private ProcessedUpdateRecord toRecord(ProcessedUpdateEntity entity) {
        return new ProcessedUpdateRecord(
            entity.getUpdateId(),
            entity.getChatId(),
            entity.getMessageId(),
            entity.getProcessedAt(),
            entity.getReplyText(),
            entity.getConversationState(),
            entity.getSuggestedReplies(),
            entity.getSentAt()
        );
    }
}
