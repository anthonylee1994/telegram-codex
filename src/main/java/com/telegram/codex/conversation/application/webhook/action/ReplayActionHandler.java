package com.telegram.codex.conversation.application.webhook.action;

import com.telegram.codex.conversation.application.update.ProcessedUpdateFlow;
import com.telegram.codex.conversation.domain.Decision;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ReplayActionHandler implements ActionHandler {

    private final ProcessedUpdateFlow processedUpdateFlow;
    private final TelegramGateway telegramClient;

    public ReplayActionHandler(ProcessedUpdateFlow processedUpdateFlow, TelegramGateway telegramClient) {
        this.processedUpdateFlow = processedUpdateFlow;
        this.telegramClient = telegramClient;
    }

    @Override
    public Decision.Action handlesAction() {
        return Decision.Action.REPLAY;
    }

    @Override
    public void execute(Decision decision, Map<String, Object> update) {
        processedUpdateFlow.resendPendingReply(decision.message(), decision.processedUpdate(), telegramClient);
    }
}
