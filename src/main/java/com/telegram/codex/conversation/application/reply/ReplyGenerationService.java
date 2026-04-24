package com.telegram.codex.conversation.application.reply;

import com.telegram.codex.conversation.application.ProcessedUpdateService;
import com.telegram.codex.conversation.application.gateway.ReplyGenerationGateway;
import com.telegram.codex.conversation.application.session.SessionService;
import com.telegram.codex.conversation.domain.memory.ChatMemoryRecord;
import com.telegram.codex.conversation.domain.session.ChatSessionRecord;
import com.telegram.codex.conversation.infrastructure.memory.ChatMemoryRepository;
import com.telegram.codex.conversation.infrastructure.memory.CodexMemoryClient;
import com.telegram.codex.conversation.infrastructure.session.ChatSessionRepository;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class ReplyGenerationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReplyGenerationService.class);

    private final ReplyGenerationGateway replyClient;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMemoryRepository chatMemoryRepository;
    private final CodexMemoryClient memoryClient;
    private final ProcessedUpdateService processedUpdateService;
    private final SessionService sessionService;
    private final TelegramGateway telegramClient;
    private final AttachmentDownloader attachmentDownloader;

    public ReplyGenerationService(
        ReplyGenerationGateway replyClient,
        ChatSessionRepository chatSessionRepository,
        ChatMemoryRepository chatMemoryRepository,
        CodexMemoryClient memoryClient,
        ProcessedUpdateService processedUpdateService,
        SessionService sessionService,
        TelegramGateway telegramClient,
        AttachmentDownloader attachmentDownloader
    ) {
        this.replyClient = replyClient;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMemoryRepository = chatMemoryRepository;
        this.memoryClient = memoryClient;
        this.processedUpdateService = processedUpdateService;
        this.sessionService = sessionService;
        this.telegramClient = telegramClient;
        this.attachmentDownloader = attachmentDownloader;
    }

    public void handle(InboundMessage message) {
        try {
            processedUpdateService.pruneIfNeeded();
            ReplyResult reply = generateReplyWithTypingStatus(message);
            deliverReply(message, reply);
        } catch (Exception error) {
            processedUpdateService.clearProcessing(message.updateId());
            throw error;
        }
    }

    private ReplyResult generateReplyWithTypingStatus(InboundMessage message) {
        return telegramClient.withTypingStatus(message.chatId(), () -> generateReply(message));
    }

    private ReplyResult generateReply(InboundMessage message) {
        List<Path> imageFilePaths = attachmentDownloader.downloadImages(message.effectiveImageFileIds());
        try {
            return replyClient.generateReply(
                message.textOrEmpty(),
                findLastResponseId(message.chatId()),
                imageFilePaths,
                message.replyToText(),
                findMemoryText(message.chatId())
            );
        } finally {
            attachmentDownloader.cleanup(imageFilePaths);
        }
    }

    private void deliverReply(InboundMessage message, ReplyResult reply) {
        processedUpdateService.savePendingReply(message.updateId(), message.chatId(), message.messageId(), reply);
        telegramClient.sendMessage(message.chatId(), reply.text(), reply.suggestedReplies(), false);
        sessionService.persistConversationState(message.chatId(), reply.conversationState());
        refreshMemory(message.chatId(), message.text(), reply.text());
        processedUpdateService.markProcessed(message.updateId(), message.chatId(), message.messageId());
    }

    private String findLastResponseId(String chatId) {
        return chatSessionRepository.findActive(chatId)
            .map(ChatSessionRecord::lastResponseId)
            .orElse(null);
    }

    private String findMemoryText(String chatId) {
        return chatMemoryRepository.find(chatId)
            .map(ChatMemoryRecord::memoryText)
            .orElse(null);
    }

    private void refreshMemory(String chatId, String userMessage, String assistantReply) {
        if (userMessage == null || userMessage.isBlank()) {
            return;
        }
        try {
            persistMemory(chatId, userMessage, assistantReply);
        } catch (Exception error) {
            LOGGER.warn("Failed to refresh long-term memory chat_id={} error={}", chatId, error.getMessage());
        }
    }

    private void persistMemory(String chatId, String userMessage, String assistantReply) {
        String existingMemory = chatMemoryRepository.find(chatId).map(ChatMemoryRecord::memoryText).orElse("");
        String mergedMemory = memoryClient.merge(existingMemory, userMessage, assistantReply);
        if (!mergedMemory.equals(existingMemory)) {
            chatMemoryRepository.persist(chatId, mergedMemory);
        }
    }
}
