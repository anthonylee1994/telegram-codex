package com.telegram.codex.integration.telegram.application.webhook;

import com.telegram.codex.conversation.domain.MessageConstants;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import com.telegram.codex.integration.telegram.domain.webhook.TelegramMessage;
import com.telegram.codex.integration.telegram.domain.webhook.TelegramUpdate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UnsupportedMessageHandler {

    private final TelegramGateway telegramClient;

    public UnsupportedMessageHandler(TelegramGateway telegramClient) {
        this.telegramClient = telegramClient;
    }

    public boolean handle(InboundMessage message, TelegramUpdate update) {
        if (message != null && !message.unsupported()) {
            return false;
        }
        if (update == null) {
            return true;
        }
        String chatId = extractChatId(update);
        if (chatId != null) {
            telegramClient.sendMessage(chatId, MessageConstants.UNSUPPORTED_MESSAGE, List.of(), false);
        }
        return true;
    }

    private String extractChatId(TelegramUpdate update) {
        TelegramMessage message = update.message();
        if (message == null || message.chat() == null || message.chat().id() == null) {
            return null;
        }
        return String.valueOf(message.chat().id());
    }
}
