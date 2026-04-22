package com.telegram.codex.conversation.webhooks.action;

import com.telegram.codex.constants.MessageConstants;
import com.telegram.codex.conversation.webhooks.Decision;
import com.telegram.codex.telegram.TelegramClient;
import com.telegram.codex.util.MapUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class UnsupportedActionHandler implements ActionHandler {

    private final TelegramClient telegramClient;

    public UnsupportedActionHandler(TelegramClient telegramClient) {
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
        Map<String, Object> message = MapUtils.castMap(update.get("message"));
        if (message == null) {
            return null;
        }
        Map<String, Object> chat = MapUtils.castMap(message.get("chat"));
        return chat == null ? null : MapUtils.stringValue(chat.get("id"));
    }
}
