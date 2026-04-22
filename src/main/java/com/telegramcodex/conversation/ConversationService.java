package com.telegramcodex.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegramcodex.codex.CliClient;
import com.telegramcodex.codex.Transcript;
import com.telegramcodex.telegram.InboundMessage;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ConversationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConversationService.class);
    private static final long PROCESSED_UPDATE_PRUNE_INTERVAL_MS = 6L * 60 * 60 * 1000;
    private static final long PROCESSED_UPDATE_RETENTION_MS = 30L * 24 * 60 * 60 * 1000;
    private static final String SUMMARY_BASELINE_MESSAGE = "以下係之前對話嘅摘要。之後請按呢份摘要延續對話上下文。";

    private final CliClient replyClient;
    private final MemoryClient memoryClient;
    private final SessionSummaryClient sessionSummaryClient;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ProcessedUpdateRepository processedUpdateRepository;
    private final ObjectMapper objectMapper;
    private final AtomicLong lastProcessedUpdatePruneAt = new AtomicLong(0);

    public ConversationService(
        CliClient replyClient,
        MemoryClient memoryClient,
        SessionSummaryClient sessionSummaryClient,
        ChatSessionRepository chatSessionRepository,
        ChatMemoryRepository chatMemoryRepository,
        ProcessedUpdateRepository processedUpdateRepository,
        ObjectMapper objectMapper
    ) {
        this.replyClient = replyClient;
        this.memoryClient = memoryClient;
        this.sessionSummaryClient = sessionSummaryClient;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMemoryRepository = chatMemoryRepository;
        this.processedUpdateRepository = processedUpdateRepository;
        this.objectMapper = objectMapper;
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

    public void persistConversationState(String chatId, String conversationState) {
        chatSessionRepository.persist(chatId, conversationState);
    }

    public void resetSession(String chatId) {
        chatSessionRepository.reset(chatId);
    }

    public Map<String, Object> memorySnapshot(String chatId) {
        Optional<ChatMemoryRecord> maybeMemory = chatMemoryRepository.find(chatId);
        if (maybeMemory.isEmpty() || maybeMemory.get().memoryText() == null || maybeMemory.get().memoryText().isBlank()) {
            return Map.of("active", false);
        }
        ChatMemoryRecord memory = maybeMemory.get();
        return Map.of(
            "active", true,
            "memory_text", memory.memoryText(),
            "last_updated_at", formatTime(memory.updatedAt())
        );
    }

    public void resetMemory(String chatId) {
        chatMemoryRepository.reset(chatId);
    }

    public Map<String, Object> sessionSnapshot(String chatId) {
        Optional<ChatSessionRecord> maybeSession = chatSessionRepository.findActive(chatId);
        if (maybeSession.isEmpty()) {
            return Map.of("active", false);
        }
        Transcript transcript = Transcript.fromConversationState(maybeSession.get().lastResponseId(), objectMapper);
        return Map.of(
            "active", true,
            "last_updated_at", formatTime(maybeSession.get().updatedAt()),
            "message_count", transcript.size(),
            "turn_count", (int) Math.ceil(transcript.size() / 2.0)
        );
    }

    public Map<String, Object> summarizeSession(String chatId) {
        Optional<ChatSessionRecord> maybeSession = chatSessionRepository.findActive(chatId);
        if (maybeSession.isEmpty()) {
            return Map.of("status", "missing_session");
        }
        Transcript transcript = Transcript.fromConversationState(maybeSession.get().lastResponseId(), objectMapper);
        if (transcript.size() < 4) {
            return Map.of("status", "too_short", "message_count", transcript.size());
        }
        String summaryText = sessionSummaryClient.summarize(transcript);
        Transcript summaryTranscript = Transcript.empty()
            .append("user", SUMMARY_BASELINE_MESSAGE)
            .append("assistant", summaryText);
        chatSessionRepository.persist(chatId, summaryTranscript.toConversationState(objectMapper));
        return Map.of(
            "status", "ok",
            "original_message_count", transcript.size(),
            "summary_text", summaryText
        );
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
        if (lastPrunedAt != 0 && now - lastPrunedAt < PROCESSED_UPDATE_PRUNE_INTERVAL_MS) {
            return;
        }
        long cutoff = now - PROCESSED_UPDATE_RETENTION_MS;
        long deletedCount = processedUpdateRepository.pruneSentBefore(cutoff);
        lastProcessedUpdatePruneAt.set(now);
        LOGGER.info("Pruned processed updates count={} cutoff={}", deletedCount, cutoff);
    }

    private String formatTime(long epochMillis) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.of("Asia/Hong_Kong"))
            .format(Instant.ofEpochMilli(epochMillis));
    }
}
