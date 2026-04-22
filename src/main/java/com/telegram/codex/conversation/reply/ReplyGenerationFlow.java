package com.telegram.codex.conversation.reply;

import com.telegram.codex.constants.MessageConstants;
import com.telegram.codex.conversation.session.SessionService;
import com.telegram.codex.documents.TextDocumentExtractor;
import com.telegram.codex.documents.TextDocumentPromptBuilder;
import com.telegram.codex.exception.MissingDependencyException;
import com.telegram.codex.telegram.AttachmentDownloader;
import com.telegram.codex.telegram.InboundMessage;
import com.telegram.codex.telegram.TelegramClient;
import com.telegram.codex.util.StringUtils;
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
    private final TelegramClient telegramClient;
    private final AttachmentDownloader attachmentDownloader;
    private final TextDocumentExtractor textDocumentExtractor;
    private final TextDocumentPromptBuilder textDocumentPromptBuilder;

    public ReplyGenerationFlow(
        ConversationService conversationService,
        SessionService sessionService,
        TelegramClient telegramClient,
        AttachmentDownloader attachmentDownloader,
        TextDocumentExtractor textDocumentExtractor,
        TextDocumentPromptBuilder textDocumentPromptBuilder
    ) {
        this.conversationService = conversationService;
        this.sessionService = sessionService;
        this.telegramClient = telegramClient;
        this.attachmentDownloader = attachmentDownloader;
        this.textDocumentExtractor = textDocumentExtractor;
        this.textDocumentPromptBuilder = textDocumentPromptBuilder;
    }

    public void call(InboundMessage message) {
        try {
            ReplyResult reply = telegramClient.withTypingStatus(message.chatId(), () -> {
                List<Path> imageFilePaths = attachmentDownloader.downloadAllAttachments(
                    message.effectiveImageFileIds(),
                    message.effectivePdfFileId()
                );
                String textOverride = buildTextOverride(message);
                try {
                    return conversationService.generateReply(message, imageFilePaths, textOverride);
                } finally {
                    attachmentDownloader.cleanup(imageFilePaths);
                }
            });

            conversationService.savePendingReply(message.updateId(), message.chatId(), message.messageId(), reply);
            telegramClient.sendMessage(message.chatId(), reply.text(), reply.suggestedReplies(), false);
            sessionService.persistConversationState(message.chatId(), reply.conversationState());
            refreshLongTermMemory(message, reply.text());
            conversationService.markProcessed(message.updateId(), message.chatId(), message.messageId());
        } catch (MissingDependencyException error) {
            conversationService.clearProcessing(message.updateId());
            LOGGER.error("Dependency unavailable update_id={} chat_id={} dependency={} error={}",
                message.updateId(), message.chatId(), error.getDependencyName(), error.getMessage());
            String errorMessage = "pdftoppm".equals(error.getDependencyName())
                ? MessageConstants.PDF_UNAVAILABLE_MESSAGE
                : MessageConstants.TEXT_DOCUMENT_UNAVAILABLE_MESSAGE;
            telegramClient.sendMessage(message.chatId(), errorMessage, List.of(), false);
        } catch (Exception error) {
            conversationService.clearProcessing(message.updateId());
            throw error;
        }
    }

    private String buildTextOverride(InboundMessage message) {
        String effectiveTextDocumentFileId = message.effectiveTextDocumentFileId();
        if (effectiveTextDocumentFileId == null) {
            return message.text();
        }
        try {
            Path documentPath = telegramClient.downloadFileToTemp(effectiveTextDocumentFileId);
            TextDocumentExtractor.ExtractionResult extractionResult = textDocumentExtractor.extract(documentPath);
            return textDocumentPromptBuilder.buildPrompt(
                message.text(),
                message.effectiveTextDocumentName(),
                extractionResult.content(),
                extractionResult.truncated(),
                message.replyingToFile()
            );
        } catch (MissingDependencyException | IllegalStateException error) {
            throw error;
        }
    }

    private void refreshLongTermMemory(InboundMessage message, String replyText) {
        if (StringUtils.isNullOrBlank(message.text())) {
            return;
        }
        try {
            conversationService.refreshLongTermMemory(message.chatId(), message.text(), replyText);
        } catch (Exception error) {
            LOGGER.warn("Failed to refresh long-term memory chat_id={} error={}", message.chatId(), error.getMessage());
        }
    }
}
