package com.telegram.codex.telegram;

import com.telegram.codex.telegram.document.DocumentTypeRegistry;
import com.telegram.codex.util.MapUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TelegramUpdateParser {

    private final DocumentTypeRegistry documentTypeRegistry;

    public TelegramUpdateParser(DocumentTypeRegistry documentTypeRegistry) {
        this.documentTypeRegistry = documentTypeRegistry;
    }

    public InboundMessage parseIncomingTelegramMessage(Map<String, Object> update) {
        if (!isValidUpdate(update)) {
            return null;
        }

        Map<String, Object> message = MapUtils.castMap(update.get("message"));
        MessageExtractor extractor = MessageExtractor.from(message, documentTypeRegistry);

        if (!extractor.isSupported()) {
            return null;
        }

        return new InboundMessage(
            extractor.getChatId(),
            extractor.getImageFileIds(),
            extractor.getMediaGroupId(),
            extractor.getMessageId(),
            extractor.getPdfFileId().orElse(null),
            List.of(),
            extractor.getReplyToMessage().map(MessageExtractor::getImageFileIds).orElse(List.of()),
            extractor.getReplyToMessage().map(MessageExtractor::getMessageId).orElse(null),
            extractor.getReplyToMessage().flatMap(MessageExtractor::getPdfFileId).orElse(null),
            extractor.getReplyToText().orElse(null),
            extractor.getReplyToMessage().flatMap(MessageExtractor::getTextDocumentFileId).orElse(null),
            extractor.getReplyToMessage().flatMap(MessageExtractor::getTextDocumentName).orElse(null),
            extractor.getText(),
            extractor.getTextDocumentFileId().orElse(null),
            extractor.getTextDocumentName().orElse(null),
            extractor.getUserId(),
            MapUtils.longValue(update.get("update_id"))
        );
    }

    private boolean isValidUpdate(Map<String, Object> update) {
        if (update == null || !(update.get("update_id") instanceof Number)) {
            return false;
        }
        Map<String, Object> message = MapUtils.castMap(update.get("message"));
        if (message == null || !(message.get("message_id") instanceof Number)) {
            return false;
        }
        return MapUtils.castMap(message.get("from")) != null
            && MapUtils.castMap(message.get("chat")) != null;
    }
}
