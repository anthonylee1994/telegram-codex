package com.telegram.codex.telegram;

import com.telegram.codex.conversation.session.SessionCompactResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CompactResultSender {

    private final TelegramClient telegramClient;

    public CompactResultSender(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    public void send(String chatId, SessionCompactResult result) {
        String text = switch (result.status()) {
            case MISSING_SESSION -> "而家冇 active session，冇嘢可以 compact。";
            case TOO_SHORT -> "目前對話得 " + result.messageCount() + " 段訊息，未去到要壓縮 context。";
            case OK -> String.join("\n",
                "已經將目前 session compact 成新 context。",
                "原本訊息：" + result.originalMessageCount(),
                "",
                result.compactText()
            );
        };
        telegramClient.sendMessage(chatId, text, List.of(), true);
    }
}
