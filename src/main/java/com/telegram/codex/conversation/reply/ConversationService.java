package com.telegram.codex.conversation.reply;

import com.telegram.codex.codex.CliClient;
import com.telegram.codex.constants.ConversationConstants;
import com.telegram.codex.conversation.memory.ChatMemoryRecord;
import com.telegram.codex.conversation.memory.ChatMemoryRepository;
import com.telegram.codex.conversation.memory.MemoryClient;
import com.telegram.codex.conversation.session.ChatSessionRecord;
import com.telegram.codex.conversation.session.ChatSessionRepository;
import com.telegram.codex.conversation.updates.ProcessedUpdateRecord;
import com.telegram.codex.conversation.updates.ProcessedUpdateRepository;
import com.telegram.codex.telegram.InboundMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ConversationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConversationService.class);

    private final CliClient replyClient;
    private final MemoryClient memoryClient;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ProcessedUpdateRepository processedUpdateRepository;
    private final AtomicLong lastProcessedUpdatePruneAt = new AtomicLong(0);

    public ConversationService(
        CliClient replyClient,
        MemoryClient memoryClient,
        ChatSessionRepository chatSessionRepository,
        ChatMemoryRepository chatMemoryRepository,
        ProcessedUpdateRepository processedUpdateRepository
    ) {
        this.replyClient = replyClient;
        this.memoryClient = memoryClient;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMemoryRepository = chatMemoryRepository;
        this.processedUpdateRepository = processedUpdateRepository;
    }

    public Optional<ProcessedUpdateRecord> getProcessedUpdate(long updateId) {
        return processedUpdateRepository.find(updateId);
    }

    public boolean beginProcessing(long updateId, String chatId, long messageId) {
        return processedUpdateRepository.beginProcessing(updateId, chatId, messageId);
    }

    public void clearProcessing(long updateId) {
        processedUpdateRepository.clearProcessing(updateId);
    }

    public void markProcessed(long updateId, String chatId, long messageId) {
        processedUpdateRepository.markProcessed(updateId, chatId, messageId);
    }

    public void savePendingReply(long updateId, String chatId, long messageId, ReplyResult result) {
        processedUpdateRepository.savePendingReply(updateId, chatId, messageId, result);
    }

    public ReplyResult generateReply(InboundMessage message, List<Path> imageFilePaths, String textOverride) {
        pruneProcessedUpdatesIfNeeded();
        Optional<ChatSessionRecord> maybeSession = chatSessionRepository.findActive(message.chatId());
        return replyClient.generateReply(
            textOverride != null ? textOverride : message.text(),
            maybeSession.map(ChatSessionRecord::lastResponseId).orElse(null),
            imageFilePaths,
            message.replyToText(),
            chatMemoryRepository.find(message.chatId()).map(ChatMemoryRecord::memoryText).orElse(null)
        );
    }

    public void refreshLongTermMemory(String chatId, String userMessage, String assistantReply) {
        String existingMemory = chatMemoryRepository.find(chatId).map(ChatMemoryRecord::memoryText).orElse("");
        String mergedMemory = memoryClient.merge(existingMemory, userMessage, assistantReply);
        if (!mergedMemory.equals(existingMemory)) {
            chatMemoryRepository.persist(chatId, mergedMemory);
        }
    }

    private void pruneProcessedUpdatesIfNeeded() {
        long now = System.currentTimeMillis();
        long lastPrunedAt = lastProcessedUpdatePruneAt.get();
        if (lastPrunedAt != 0 && now - lastPrunedAt < ConversationConstants.PROCESSED_UPDATE_PRUNE_INTERVAL_MS) {
            return;
        }
        long cutoff = now - ConversationConstants.PROCESSED_UPDATE_RETENTION_MS;
        long deletedCount = processedUpdateRepository.pruneSentBefore(cutoff);
        lastProcessedUpdatePruneAt.set(now);
        LOGGER.info("Pruned processed updates count={} cutoff={}", deletedCount, cutoff);
    }
}
