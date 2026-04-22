package com.telegram.codex.telegram;

import com.telegram.codex.conversation.session.SessionSummaryResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SummaryResultSender {

    private final TelegramClient telegramClient;

    public SummaryResultSender(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    public void send(String chatId, SessionSummaryResult result) {
        String text = switch (result.status()) {
            case MISSING_SESSION -> "而家冇 active session，冇嘢可以摘要。";
            case TOO_SHORT -> "目前對話得 " + result.messageCount() + " 段訊息，未去到要壓縮 context。";
            case OK -> String.join("\n",
                "已經將目前 session 壓縮成新 context。",
                "原本訊息：" + result.originalMessageCount(),
                "",
                result.summaryText()
            );
        };
        telegramClient.sendMessage(chatId, text, List.of(), true);
    }
}
