package com.telegram.codex.conversation.reply;

import com.telegram.codex.constants.MessageConstants;
import com.telegram.codex.conversation.session.SessionService;
import com.telegram.codex.documents.PdfPageRasterizer;
import com.telegram.codex.documents.TextDocumentExtractor;
import com.telegram.codex.exception.MissingDependencyException;
import com.telegram.codex.telegram.InboundMessage;
import com.telegram.codex.telegram.TelegramClient;
import com.telegram.codex.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class ReplyGenerationFlow {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReplyGenerationFlow.class);

    private final ConversationService conversationService;
    private final SessionService sessionService;
    private final TelegramClient telegramClient;
    private final PdfPageRasterizer pdfPageRasterizer;
    private final TextDocumentExtractor textDocumentExtractor;

    public ReplyGenerationFlow(
        ConversationService conversationService,
        SessionService sessionService,
        TelegramClient telegramClient,
        PdfPageRasterizer pdfPageRasterizer,
        TextDocumentExtractor textDocumentExtractor
    ) {
        this.conversationService = conversationService;
        this.sessionService = sessionService;
        this.telegramClient = telegramClient;
        this.pdfPageRasterizer = pdfPageRasterizer;
        this.textDocumentExtractor = textDocumentExtractor;
    }

    public void call(InboundMessage message) {
        boolean hasPendingReply = false;
        try {
            ReplyResult reply = telegramClient.withTypingStatus(message.chatId(), () -> {
                List<Path> imageFilePaths = downloadAttachmentsIfNeeded(message);
                String textOverride = buildTextOverride(message);
                try {
                    return conversationService.generateReply(message, imageFilePaths, textOverride);
                } finally {
                    cleanupDownloadedArtifacts(imageFilePaths);
                }
            });

            conversationService.savePendingReply(message.updateId(), message.chatId(), message.messageId(), reply);
            hasPendingReply = true;
            telegramClient.sendMessage(message.chatId(), reply.text(), reply.suggestedReplies(), false);
            sessionService.persistConversationState(message.chatId(), reply.conversationState());
            refreshLongTermMemory(message, reply.text());
            conversationService.markProcessed(message.updateId(), message.chatId(), message.messageId());
        } catch (MissingDependencyException error) {
            if (!hasPendingReply) {
                conversationService.clearProcessing(message.updateId());
            }
            LOGGER.error("Dependency unavailable update_id={} chat_id={} dependency={} error={}",
                message.updateId(), message.chatId(), error.getDependencyName(), error.getMessage());
            String errorMessage = "pdftoppm".equals(error.getDependencyName())
                ? MessageConstants.PDF_UNAVAILABLE_MESSAGE
                : MessageConstants.TEXT_DOCUMENT_UNAVAILABLE_MESSAGE;
            telegramClient.sendMessage(message.chatId(), errorMessage, List.of(), false);
        } catch (Exception error) {
            if (!hasPendingReply) {
                conversationService.clearProcessing(message.updateId());
            }
            throw error;
        }
    }

    private List<Path> downloadAttachmentsIfNeeded(InboundMessage message) {
        ArrayList<Path> imagePaths = new ArrayList<>();
        for (String imageFileId : message.effectiveImageFileIds()) {
            imagePaths.add(telegramClient.downloadFileToTemp(imageFileId));
        }
        String effectivePdfFileId = message.effectivePdfFileId();
        if (effectivePdfFileId == null) {
            return List.copyOf(imagePaths);
        }
        Path pdfPath = telegramClient.downloadFileToTemp(effectivePdfFileId);
        imagePaths.addAll(pdfPageRasterizer.rasterize(pdfPath));
        return List.copyOf(imagePaths);
    }

    private String buildTextOverride(InboundMessage message) {
        String effectiveTextDocumentFileId = message.effectiveTextDocumentFileId();
        if (effectiveTextDocumentFileId == null) {
            return message.text();
        }
        try {
            Path documentPath = telegramClient.downloadFileToTemp(effectiveTextDocumentFileId);
            TextDocumentExtractor.ExtractionResult extractionResult = textDocumentExtractor.extract(documentPath);
            String basePrompt;
            if (message.text() != null && !message.text().isBlank()) {
                basePrompt = message.text();
            } else if (message.replyingToFile()) {
                basePrompt = "我引用咗之前一份文字檔。請先概括內容，再按內容回答。";
            } else {
                basePrompt = "我上載咗一份文字檔。請先概括內容，再按內容回答。";
            }
            return String.join("\n", basePrompt, "", "檔案名稱：" + (message.effectiveTextDocumentName() == null ? "未命名檔案" : message.effectiveTextDocumentName()), "以下係檔案內容：", "```text", extractionResult.content(), "```", extractionResult.truncated() ? "注意：檔案內容已經截短，只包含前面一部分。" : "").trim();
        } catch (MissingDependencyException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException("Failed to build text override", error);
        }
    }

    private void cleanupDownloadedArtifacts(List<Path> imageFilePaths) {
        for (Path imageFilePath : imageFilePaths) {
            StreamUtils.deleteDirectoryRecursively(imageFilePath.getParent());
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
