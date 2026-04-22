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
public class ShowSessionActionHandler implements ActionHandler {

    private final SessionService sessionService;
    private final TelegramClient telegramClient;
    private final ProcessedUpdateFlow processedUpdateFlow;

    public ShowSessionActionHandler(
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
        return Decision.Action.SHOW_SESSION;
    }

    @Override
    public void execute(Decision decision, Map<String, Object> update) {
        String sessionMessage = buildSessionMessage(decision.message().chatId());
        telegramClient.sendMessage(decision.message().chatId(), sessionMessage, List.of(), true);
        processedUpdateFlow.markProcessed(decision.message());
    }

    private String buildSessionMessage(String chatId) {
        SessionSnapshot snapshot = sessionService.snapshot(chatId);
        if (!snapshot.active()) {
            return "目前未有已生效 session。你可以直接 send 訊息開始，或者之後用 /summary 壓縮長對話。";
        }
        return String.join("\n",
            "目前 session：已生效",
            "訊息數：" + snapshot.messageCount(),
            "大概輪數：" + snapshot.turnCount(),
            "最後更新：" + snapshot.lastUpdatedAt(),
            "想壓縮 context 可以用 /summary。"
        );
    }
}
