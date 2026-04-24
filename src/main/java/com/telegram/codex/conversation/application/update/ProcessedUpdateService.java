package com.telegram.codex.conversation.application.update;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.conversation.application.reply.ReplyResult;
import com.telegram.codex.conversation.application.session.SessionService;
import com.telegram.codex.conversation.domain.ConversationConstants;
import com.telegram.codex.conversation.domain.update.ProcessedUpdateRecord;
import com.telegram.codex.conversation.infrastructure.update.ProcessedUpdateRepository;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ProcessedUpdateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessedUpdateService.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final ProcessedUpdateRepository processedUpdateRepository;
    private final SessionService sessionService;
    private final AtomicLong lastProcessedUpdatePruneAt = new AtomicLong(0);

    public ProcessedUpdateService(
        ObjectMapper objectMapper,
        ProcessedUpdateRepository processedUpdateRepository,
        SessionService sessionService
    ) {
        this.objectMapper = objectMapper;
        this.processedUpdateRepository = processedUpdateRepository;
        this.sessionService = sessionService;
    }

    public Optional<ProcessedUpdateRecord> find(long updateId) {
        return processedUpdateRepository.find(updateId);
    }

    public boolean beginProcessing(InboundMessage message) {
        ArrayList<Long> claimedUpdateIds = new ArrayList<>();
        for (InboundMessage.ProcessingUpdate processingUpdate : message.processingUpdates()) {
            boolean claimed = processedUpdateRepository.beginProcessing(
                processingUpdate.updateId(),
                message.chatId(),
                processingUpdate.messageId()
            );
            if (!claimed) {
                rollbackProcessingClaims(claimedUpdateIds);
                return false;
            }
            claimedUpdateIds.add(processingUpdate.updateId());
        }
        return true;
    }

    public void clearProcessing(long updateId) {
        processedUpdateRepository.clearProcessing(updateId);
    }

    public boolean duplicate(Optional<ProcessedUpdateRecord> processedUpdate) {
        return processedUpdate.map(ProcessedUpdateRecord::sentAt).orElse(null) != null;
    }

    public boolean replayable(Optional<ProcessedUpdateRecord> processedUpdate) {
        return processedUpdate
            .filter(update -> update.replyText() != null && update.conversationState() != null)
            .isPresent();
    }

    public void resendPendingReply(InboundMessage message, ProcessedUpdateRecord processedUpdate, TelegramGateway telegramClient) {
        telegramClient.sendMessage(
            message.chatId(),
            processedUpdate.replyText(),
            parseStoredSuggestedReplies(processedUpdate.suggestedReplies()),
            false
        );
        sessionService.persistConversationState(message.chatId(), processedUpdate.conversationState());
        markProcessed(message);
    }

    public void markProcessed(long updateId, String chatId, long messageId) {
        processedUpdateRepository.markProcessed(updateId, chatId, messageId);
    }

    public void markProcessed(InboundMessage message) {
        for (InboundMessage.ProcessingUpdate processingUpdate : message.processingUpdates()) {
            processedUpdateRepository.markProcessed(processingUpdate.updateId(), message.chatId(), processingUpdate.messageId());
        }
    }

    public void savePendingReply(long updateId, String chatId, long messageId, ReplyResult result) {
        processedUpdateRepository.savePendingReply(updateId, chatId, messageId, result);
    }

    public void pruneIfNeeded() {
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

    private void rollbackProcessingClaims(List<Long> claimedUpdateIds) {
        for (Long updateId : claimedUpdateIds) {
            processedUpdateRepository.clearProcessing(updateId);
        }
    }

    List<String> parseStoredSuggestedReplies(String rawSuggestedReplies) {
        if (rawSuggestedReplies == null || rawSuggestedReplies.isBlank()) {
            return List.of();
        }
        try {
            List<String> replies = objectMapper.readValue(rawSuggestedReplies, STRING_LIST);
            return replies == null ? List.of() : replies.stream()
                .filter(reply -> reply != null && !reply.isBlank())
                .map(String::trim)
                .toList();
        } catch (Exception error) {
            return List.of();
        }
    }
}
