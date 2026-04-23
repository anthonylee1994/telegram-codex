package com.telegram.codex.conversation.application.reply;

import com.telegram.codex.conversation.application.port.out.AttachmentDownloadPort;
import com.telegram.codex.conversation.application.session.SessionService;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class ReplyGenerationFlow {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReplyGenerationFlow.class);

    private final ConversationService conversationService;
    private final SessionService sessionService;
    private final TelegramGateway telegramClient;
    private final AttachmentDownloadPort attachmentDownloader;

    public ReplyGenerationFlow(
        ConversationService conversationService,
        SessionService sessionService,
        TelegramGateway telegramClient,
        AttachmentDownloadPort attachmentDownloader
    ) {
        this.conversationService = conversationService;
        this.sessionService = sessionService;
        this.telegramClient = telegramClient;
        this.attachmentDownloader = attachmentDownloader;
    }

    public void call(InboundMessage message) {
        try {
            ReplyResult reply = telegramClient.withTypingStatus(message.chatId(), () -> {
                List<Path> imageFilePaths = attachmentDownloader.downloadImages(message.effectiveImageFileIds());
                try {
                    return conversationService.generateReply(message, imageFilePaths, message.text());
                } finally {
                    attachmentDownloader.cleanup(imageFilePaths);
                }
            });

            conversationService.savePendingReply(message.updateId(), message.chatId(), message.messageId(), reply);
            telegramClient.sendMessage(message.chatId(), reply.text(), reply.suggestedReplies(), false);
            sessionService.persistConversationState(message.chatId(), reply.conversationState());
            refreshLongTermMemory(message, reply.text());
            conversationService.markProcessed(message.updateId(), message.chatId(), message.messageId());
        } catch (Exception error) {
            conversationService.clearProcessing(message.updateId());
            throw error;
        }
    }

    private void refreshLongTermMemory(InboundMessage message, String replyText) {
        if (message.text() == null || message.text().isBlank()) {
            return;
        }
        try {
            conversationService.refreshLongTermMemory(message.chatId(), message.text(), replyText);
        } catch (Exception error) {
            LOGGER.warn("Failed to refresh long-term memory chat_id={} error={}", message.chatId(), error.getMessage());
        }
    }
}
