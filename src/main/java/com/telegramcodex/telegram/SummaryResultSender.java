package com.telegramcodex.telegram;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SummaryResultSender {

    private final TelegramClient telegramClient;

    public SummaryResultSender(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    public void send(String chatId, Map<String, Object> result) {
        String status = String.valueOf(result.get("status"));
        String text = switch (status) {
            case "missing_session" -> "而家冇 active session，冇嘢可以摘要。";
            case "too_short" -> "目前對話得 " + result.get("message_count") + " 段訊息，未去到要壓縮 context。";
            case "ok" -> String.join("\n",
                "已經將目前 session 壓縮成新 context。",
                "原本訊息：" + result.get("original_message_count"),
                "",
                String.valueOf(result.get("summary_text"))
            );
            default -> TelegramWebhookHandler.GENERIC_ERROR_MESSAGE;
        };
        telegramClient.sendMessage(chatId, text, List.of(), true);
    }
}
