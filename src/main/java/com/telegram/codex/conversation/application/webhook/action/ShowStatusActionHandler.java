package com.telegram.codex.conversation.application.webhook.action;

import com.telegram.codex.conversation.application.session.SessionService;
import com.telegram.codex.conversation.application.update.ProcessedUpdateFlow;
import com.telegram.codex.conversation.domain.Decision;
import com.telegram.codex.conversation.domain.session.SessionSnapshot;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ShowStatusActionHandler implements ActionHandler {

    private final SessionService sessionService;
    private final TelegramGateway telegramClient;
    private final ProcessedUpdateFlow processedUpdateFlow;

    public ShowStatusActionHandler(
        SessionService sessionService,
        TelegramGateway telegramClient,
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
