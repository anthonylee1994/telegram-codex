package com.telegram.codex.conversation.webhooks.action;

import com.telegram.codex.conversation.session.SessionService;
import com.telegram.codex.conversation.session.SessionSnapshot;
import com.telegram.codex.conversation.updates.ProcessedUpdateFlow;
import com.telegram.codex.conversation.webhooks.Decision;
import com.telegram.codex.telegram.TelegramClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ShowStatusActionHandler implements ActionHandler {

    private final SessionService sessionService;
    private final TelegramClient telegramClient;
    private final ProcessedUpdateFlow processedUpdateFlow;

    public ShowStatusActionHandler(
        SessionService sessionService,
        TelegramClient telegramClient,
        ProcessedUpdateFlow processedUpdateFlow
    ) {
        this.sessionService = sessionService;
        this.telegramClient = telegramClient;
        this.processedUpdateFlow = processedUpdateFlow;
    }

    @Override
    public Decision.Action handlesAction() {
        return Decision.Action.SHOW_STATUS;
    }

    @Override
    public void execute(Decision decision, Map<String, Object> update) {
        String statusMessage = buildStatusMessage(decision.message().chatId());
        telegramClient.sendMessage(decision.message().chatId(), statusMessage, List.of(), true);
        processedUpdateFlow.markProcessed(decision.message());
    }

    private String buildStatusMessage(String chatId) {
        SessionSnapshot snapshot = sessionService.snapshot(chatId);
        return String.join("\n",
            "Bot 狀態：OK 🤖",
            "Session 狀態：" + (snapshot.active() ? "已生效 ✅" : "未生效 ❌"),
            "只支持：文字、圖片"
        );
    }
}
