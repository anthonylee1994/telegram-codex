package com.telegram.codex.conversation.application.webhook.action;

import com.telegram.codex.conversation.domain.Decision;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import com.telegram.codex.conversation.domain.MessageConstants;
import com.telegram.codex.integration.telegram.domain.TelegramPayloadValueReader;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class UnsupportedActionHandler implements ActionHandler {

    private final TelegramGateway telegramClient;

    public UnsupportedActionHandler(TelegramGateway telegramClient) {
        this.telegramClient = telegramClient;
    }

    @Override
    public Decision.Action handlesAction() {
        return Decision.Action.UNSUPPORTED;
    }

    @Override
    public void execute(Decision decision, Map<String, Object> update) {
        if (update == null) {
            return;
        }
        String chatId = extractChatId(update);
        if (chatId != null) {
            telegramClient.sendMessage(chatId, MessageConstants.UNSUPPORTED_MESSAGE, List.of(), false);
        }
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
