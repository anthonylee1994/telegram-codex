package com.telegram.codex.integration.telegram.application.webhook;

import com.telegram.codex.conversation.domain.MessageConstants;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import com.telegram.codex.integration.telegram.domain.TelegramPayloadValueReader;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class UnsupportedMessageHandler {

    private final TelegramGateway telegramClient;

    public UnsupportedMessageHandler(TelegramGateway telegramClient) {
        this.telegramClient = telegramClient;
    }

    public boolean handle(InboundMessage message, Map<String, Object> update) {
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

    private String extractChatId(Map<String, Object> update) {
        Map<String, Object> message = TelegramPayloadValueReader.castMap(update.get("message"));
        if (message == null) {
            return null;
        }
        Map<String, Object> chat = TelegramPayloadValueReader.castMap(message.get("chat"));
        return chat == null ? null : TelegramPayloadValueReader.stringValue(chat.get("id"));
    }
}
