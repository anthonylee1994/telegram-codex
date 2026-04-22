package com.telegram.codex.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.persistence.MediaGroupBufferEntity;
import com.telegram.codex.persistence.MediaGroupBufferJpaRepository;
import com.telegram.codex.persistence.MediaGroupMessageEntity;
import com.telegram.codex.persistence.MediaGroupMessageJpaRepository;
import com.telegram.codex.telegram.InboundMessage;
import com.telegram.codex.util.JsonSerializer;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MediaGroupStore {

    private final MediaGroupBufferJpaRepository bufferRepository;
    private final MediaGroupMessageJpaRepository messageRepository;
    private final MediaGroupMerger mediaGroupMerger;
    private final ObjectMapper objectMapper;

    public MediaGroupStore(
        MediaGroupBufferJpaRepository bufferRepository,
        MediaGroupMessageJpaRepository messageRepository,
        MediaGroupMerger mediaGroupMerger,
        ObjectMapper objectMapper
    ) {
        this.bufferRepository = bufferRepository;
        this.messageRepository = messageRepository;
        this.mediaGroupMerger = mediaGroupMerger;
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
        List<InboundMessage> messages = rows.stream().map(this::readMessage).toList();
        return FlushResult.ready(mediaGroupMerger.merge(messages));
    }

    @Transactional
    public void clear() {
        messageRepository.deleteAll();
        bufferRepository.deleteAll();
    }

    private String writeMessage(InboundMessage message) {
        return JsonSerializer.serialize(objectMapper, message);
    }

    private InboundMessage readMessage(MediaGroupMessageEntity entity) {
        return JsonSerializer.deserialize(objectMapper, entity.getPayload(), InboundMessage.class);
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
