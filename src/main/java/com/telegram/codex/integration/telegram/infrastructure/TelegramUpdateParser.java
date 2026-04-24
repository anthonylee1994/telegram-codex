package com.telegram.codex.integration.telegram.infrastructure;

import com.telegram.codex.integration.telegram.application.port.in.TelegramMessageParser;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import com.telegram.codex.integration.telegram.domain.MessageExtractor;
import com.telegram.codex.integration.telegram.domain.TelegramPayloadValueReader;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TelegramUpdateParser implements TelegramMessageParser {

    @Override
    public InboundMessage parseIncomingTelegramMessage(Map<String, Object> update) {
        Map<String, Object> message = extractMessage(update);
        if (!isValidUpdate(update, message)) {
            return null;
        }
        MessageExtractor extractor = MessageExtractor.from(message);
        if (!extractor.isSupported()) {
            return null;
        }
        return buildInboundMessage(update, extractor);
    }

    private InboundMessage buildInboundMessage(Map<String, Object> update, MessageExtractor extractor) {
        MessageExtractor replyToMessage = extractor.getReplyToMessage().orElse(null);
        return new InboundMessage(
            extractor.getChatId(),
            extractor.getImageFileIds(),
            extractor.getMediaGroupId(),
            extractor.getMessageId(),
            List.of(),
            replyToMessage == null ? List.of() : replyToMessage.getImageFileIds(),
            replyToMessage == null ? null : replyToMessage.getMessageId(),
            extractor.getReplyToText().orElse(null),
            extractor.getText(),
            extractor.getUserId(),
            TelegramPayloadValueReader.longValue(update.get("update_id"))
        );
    }

    private Map<String, Object> extractMessage(Map<String, Object> update) {
        return update == null ? null : TelegramPayloadValueReader.castMap(update.get("message"));
    }

    private boolean isValidUpdate(Map<String, Object> update, Map<String, Object> message) {
        if (update == null || !(update.get("update_id") instanceof Number)) {
            return false;
        }
        if (message == null || !(message.get("message_id") instanceof Number)) {
            return false;
        }
        return TelegramPayloadValueReader.castMap(message.get("from")) != null
            && TelegramPayloadValueReader.castMap(message.get("chat")) != null;
    }
}
