package com.telegram.codex.conversation.webhooks.action;

import com.telegram.codex.conversation.updates.ProcessedUpdateFlow;
import com.telegram.codex.conversation.webhooks.Decision;
import com.telegram.codex.telegram.TelegramClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RejectSensitiveIntentActionHandler implements ActionHandler {

    private final TelegramClient telegramClient;
    private final ProcessedUpdateFlow processedUpdateFlow;

    public RejectSensitiveIntentActionHandler(TelegramClient telegramClient, ProcessedUpdateFlow processedUpdateFlow) {
        this.telegramClient = telegramClient;
        this.processedUpdateFlow = processedUpdateFlow;
    }

    @Override
    public Decision.Action handlesAction() {
        return Decision.Action.REJECT_SENSITIVE_INTENT;
    }

    @Override
    public void execute(Decision decision, Map<String, Object> update) {
        telegramClient.sendMessage(decision.message().chatId(), decision.responseText(), List.of(), false);
        processedUpdateFlow.markProcessed(decision.message());
    }
}
