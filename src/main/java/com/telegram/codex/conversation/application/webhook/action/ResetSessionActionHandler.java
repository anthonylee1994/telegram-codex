package com.telegram.codex.conversation.application.webhook.action;

import com.telegram.codex.conversation.application.session.SessionService;
import com.telegram.codex.conversation.application.update.ProcessedUpdateFlow;
import com.telegram.codex.conversation.domain.Decision;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ResetSessionActionHandler implements ActionHandler {

    private final SessionService sessionService;
    private final TelegramGateway telegramClient;
    private final ProcessedUpdateFlow processedUpdateFlow;

    public ResetSessionActionHandler(
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
        return Decision.Action.RESET_SESSION;
    }

    @Override
    public void execute(Decision decision, Map<String, Object> update) {
        sessionService.reset(decision.message().chatId());
        telegramClient.sendMessage(decision.message().chatId(), decision.responseText(), List.of(), true);
        processedUpdateFlow.markProcessed(decision.message());
    }
}
