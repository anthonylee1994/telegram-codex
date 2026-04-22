package com.telegram.codex.conversation.webhooks.action;

import com.telegram.codex.conversation.updates.ProcessedUpdateFlow;
import com.telegram.codex.conversation.webhooks.Decision;
import com.telegram.codex.telegram.TelegramClient;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ReplayActionHandler implements ActionHandler {

    private final ProcessedUpdateFlow processedUpdateFlow;
    private final TelegramClient telegramClient;

    public ReplayActionHandler(ProcessedUpdateFlow processedUpdateFlow, TelegramClient telegramClient) {
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
