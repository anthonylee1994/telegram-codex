package com.telegram.codex.integration.telegram.infrastructure;

import com.telegram.codex.integration.telegram.application.port.in.TelegramMessageParser;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import com.telegram.codex.integration.telegram.domain.MessageExtractor;
import com.telegram.codex.integration.telegram.domain.webhook.TelegramMessage;
import com.telegram.codex.integration.telegram.domain.webhook.TelegramUpdate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TelegramUpdateParser implements TelegramMessageParser {

    @Override
    public InboundMessage parseIncomingTelegramMessage(TelegramUpdate update) {
        TelegramMessage message = extractMessage(update);
        if (!isValidUpdate(update, message)) {
            return null;
        }
        MessageExtractor extractor = MessageExtractor.from(message);
        if (!extractor.isSupported()) {
            return null;
        }
        return buildInboundMessage(update, extractor);
    }

    private InboundMessage buildInboundMessage(TelegramUpdate update, MessageExtractor extractor) {
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
            update.updateId()
        );
    }

    private TelegramMessage extractMessage(TelegramUpdate update) {
        return update == null ? null : update.message();
    }

    private boolean isValidUpdate(TelegramUpdate update, TelegramMessage message) {
        if (update == null || update.updateId() == null) {
            return false;
        }
        if (message == null || message.messageId() == null) {
            return false;
        }
        return message.from() != null && message.chat() != null;
    }
}
