package com.telegram.codex.conversation.application.reply;

import com.telegram.codex.conversation.application.gateway.AttachmentDownloadGateway;
import com.telegram.codex.conversation.application.gateway.ReplyGenerationGateway;
import com.telegram.codex.conversation.application.session.SessionService;
import com.telegram.codex.conversation.application.update.ProcessedUpdateService;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class ReplyGenerationService {

    private final ReplyGenerationGateway replyClient;
    private final ReplyContextLoader replyContextLoader;
    private final LongTermMemoryRefresher longTermMemoryRefresher;
    private final ProcessedUpdateService processedUpdateService;
    private final SessionService sessionService;
    private final TelegramGateway telegramClient;
    private final AttachmentDownloadGateway attachmentDownloader;

    public ReplyGenerationService(
        ReplyGenerationGateway replyClient,
        ReplyContextLoader replyContextLoader,
        LongTermMemoryRefresher longTermMemoryRefresher,
        ProcessedUpdateService processedUpdateService,
        SessionService sessionService,
        TelegramGateway telegramClient,
        AttachmentDownloadGateway attachmentDownloader
    ) {
        this.replyClient = replyClient;
        this.replyContextLoader = replyContextLoader;
        this.longTermMemoryRefresher = longTermMemoryRefresher;
        this.processedUpdateService = processedUpdateService;
        this.sessionService = sessionService;
        this.telegramClient = telegramClient;
        this.attachmentDownloader = attachmentDownloader;
    }

    public void handle(InboundMessage message) {
        try {
            processedUpdateService.pruneIfNeeded();
            ReplyResult reply = telegramClient.withTypingStatus(message.chatId(), () -> {
                ReplyContextSnapshot context = replyContextLoader.load(message);
                List<Path> imageFilePaths = attachmentDownloader.downloadImages(message.effectiveImageFileIds());
                try {
                    return generateReply(context, imageFilePaths);
                } finally {
                    attachmentDownloader.cleanup(imageFilePaths);
                }
            });

            processedUpdateService.savePendingReply(message.updateId(), message.chatId(), message.messageId(), reply);
            telegramClient.sendMessage(message.chatId(), reply.text(), reply.suggestedReplies(), false);
            sessionService.persistConversationState(message.chatId(), reply.conversationState());
            longTermMemoryRefresher.refresh(message.chatId(), message.text(), reply.text());
            processedUpdateService.markProcessed(message.updateId(), message.chatId(), message.messageId());
        } catch (Exception error) {
            processedUpdateService.clearProcessing(message.updateId());
            throw error;
        }
    }

    private ReplyResult generateReply(ReplyContextSnapshot context, List<Path> imageFilePaths) {
        return replyClient.generateReply(
            context.promptText(),
            context.lastResponseId(),
            imageFilePaths,
            context.replyToText(),
            context.memoryText()
        );
    }
}
