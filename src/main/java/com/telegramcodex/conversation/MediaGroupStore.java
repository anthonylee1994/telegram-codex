package com.telegramcodex.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegramcodex.persistence.MediaGroupBufferEntity;
import com.telegramcodex.persistence.MediaGroupBufferJpaRepository;
import com.telegramcodex.persistence.MediaGroupMessageEntity;
import com.telegramcodex.persistence.MediaGroupMessageJpaRepository;
import com.telegramcodex.telegram.InboundMessage;
import jakarta.transaction.Transactional;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MediaGroupStore {

    private final MediaGroupBufferJpaRepository bufferRepository;
    private final MediaGroupMessageJpaRepository messageRepository;
    private final ObjectMapper objectMapper;

    public MediaGroupStore(
        MediaGroupBufferJpaRepository bufferRepository,
        MediaGroupMessageJpaRepository messageRepository,
        ObjectMapper objectMapper
    ) {
        this.bufferRepository = bufferRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EnqueueResult enqueue(InboundMessage message, double waitDurationSeconds) {
        long deadlineAt = System.currentTimeMillis() + Math.round(waitDurationSeconds * 1000.0);
        String key = buildKey(message);

        MediaGroupBufferEntity buffer = new MediaGroupBufferEntity();
        buffer.setKey(key);
        buffer.setDeadlineAt(deadlineAt);
        bufferRepository.save(buffer);

        MediaGroupMessageEntity row = new MediaGroupMessageEntity();
        row.setUpdateId(message.updateId());
        row.setMediaGroupKey(key);
        row.setMessageId(message.messageId());
        row.setPayload(writeMessage(message));
        messageRepository.save(row);

        return new EnqueueResult(deadlineAt, key);
    }

    @Transactional
    public FlushResult flush(String key, long expectedDeadlineAt) {
        MediaGroupBufferEntity buffer = bufferRepository.findById(key).orElse(null);
        if (buffer == null) {
            return FlushResult.missing();
        }
        if (buffer.getDeadlineAt() != expectedDeadlineAt) {
            return FlushResult.stale();
        }
        long waitDurationMs = buffer.getDeadlineAt() - System.currentTimeMillis();
        if (waitDurationMs > 0) {
            return FlushResult.pending(waitDurationMs / 1000.0);
        }
        List<MediaGroupMessageEntity> rows = messageRepository.findByMediaGroupKeyOrderByMessageIdAscUpdateIdAsc(key);
        messageRepository.deleteByMediaGroupKey(key);
        bufferRepository.delete(buffer);
        if (rows.isEmpty()) {
            return FlushResult.missing();
        }
        return FlushResult.ready(aggregateMessages(rows));
    }

    @Transactional
    public void clear() {
        messageRepository.deleteAll();
        bufferRepository.deleteAll();
    }

    private InboundMessage aggregateMessages(List<MediaGroupMessageEntity> rows) {
        List<InboundMessage> messages = rows.stream()
            .map(this::readMessage)
            .sorted(Comparator.comparingLong(InboundMessage::messageId).thenComparingLong(InboundMessage::updateId))
            .toList();
        InboundMessage primary = messages.getFirst();
        String aggregatedText = messages.stream()
            .map(InboundMessage::text)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(null);
        List<String> aggregatedImageFileIds = messages.stream()
            .flatMap(message -> message.imageFileIds().stream())
            .distinct()
            .toList();
        List<InboundMessage.ProcessingUpdate> processingUpdates = messages.stream()
            .map(message -> new InboundMessage.ProcessingUpdate(message.updateId(), message.messageId()))
            .toList();
        return new InboundMessage(
            primary.chatId(),
            aggregatedImageFileIds,
            primary.mediaGroupId(),
            primary.messageId(),
            null,
            processingUpdates,
            List.of(),
            null,
            null,
            null,
            null,
            null,
            aggregatedText,
            null,
            null,
            primary.userId(),
            primary.updateId()
        );
    }

    private String writeMessage(InboundMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to persist media group payload", error);
        }
    }

    private InboundMessage readMessage(MediaGroupMessageEntity entity) {
        try {
            return objectMapper.readValue(entity.getPayload(), InboundMessage.class);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to read media group payload", error);
        }
    }

    private String buildKey(InboundMessage message) {
        return message.chatId() + ":" + message.mediaGroupId();
    }

    public record EnqueueResult(long deadlineAt, String key) {
    }

    public record FlushResult(String status, InboundMessage message, Double waitDurationSeconds) {
        public static FlushResult missing() {
            return new FlushResult("missing", null, null);
        }

        public static FlushResult stale() {
            return new FlushResult("stale", null, null);
        }

        public static FlushResult pending(double waitDurationSeconds) {
            return new FlushResult("pending", null, waitDurationSeconds);
        }

        public static FlushResult ready(InboundMessage message) {
            return new FlushResult("ready", message, null);
        }
    }
}
