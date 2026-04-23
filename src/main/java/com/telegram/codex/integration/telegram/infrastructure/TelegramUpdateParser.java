package com.telegram.codex.integration.telegram.infrastructure;

import com.telegram.codex.integration.telegram.application.port.in.TelegramMessageParser;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import com.telegram.codex.integration.telegram.domain.MessageExtractor;
import com.telegram.codex.integration.telegram.domain.TelegramPayloadValueReader;
import com.telegram.codex.integration.telegram.domain.document.DocumentTypeRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TelegramUpdateParser implements TelegramMessageParser {

    private final DocumentTypeRegistry documentTypeRegistry;

    public TelegramUpdateParser(DocumentTypeRegistry documentTypeRegistry) {
        this.documentTypeRegistry = documentTypeRegistry;
    }

    @Override
    public InboundMessage parseIncomingTelegramMessage(Map<String, Object> update) {
        if (!isValidUpdate(update)) {
            return null;
        }

        Map<String, Object> message = TelegramPayloadValueReader.castMap(update.get("message"));
        MessageExtractor extractor = MessageExtractor.from(message, documentTypeRegistry);

        if (!extractor.isSupported()) {
            return null;
        }

        return new InboundMessage(
            extractor.getChatId(),
            extractor.getImageFileIds(),
            extractor.getMediaGroupId(),
            extractor.getMessageId(),
            List.of(),
            extractor.getReplyToMessage().map(MessageExtractor::getImageFileIds).orElse(List.of()),
            extractor.getReplyToMessage().map(MessageExtractor::getMessageId).orElse(null),
            extractor.getReplyToText().orElse(null),
            extractor.getText(),
            extractor.getUserId(),
            TelegramPayloadValueReader.longValue(update.get("update_id"))
        );
    }

    private boolean isValidUpdate(Map<String, Object> update) {
        if (update == null || !(update.get("update_id") instanceof Number)) {
            return false;
        }
        Map<String, Object> message = TelegramPayloadValueReader.castMap(update.get("message"));
        if (message == null || !(message.get("message_id") instanceof Number)) {
            return false;
        }
        return TelegramPayloadValueReader.castMap(message.get("from")) != null
            && TelegramPayloadValueReader.castMap(message.get("chat")) != null;
    }
}
