package com.telegram.codex.conversation.webhooks.action;

import com.telegram.codex.constants.MessageConstants;
import com.telegram.codex.conversation.updates.ProcessedUpdateFlow;
import com.telegram.codex.conversation.webhooks.Decision;
import com.telegram.codex.telegram.TelegramClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RateLimitedActionHandler implements ActionHandler {

    private final TelegramClient telegramClient;
    private final ProcessedUpdateFlow processedUpdateFlow;

    public RateLimitedActionHandler(TelegramClient telegramClient, ProcessedUpdateFlow processedUpdateFlow) {
        this.telegramClient = telegramClient;
        this.processedUpdateFlow = processedUpdateFlow;
    }

    @Override
    public Decision.Action handlesAction() {
        return Decision.Action.RATE_LIMITED;
    }

    @Override
    public void execute(Decision decision, Map<String, Object> update) {
        telegramClient.sendMessage(decision.message().chatId(), MessageConstants.RATE_LIMIT_MESSAGE, List.of(), false);
        processedUpdateFlow.markProcessed(decision.message());
    }
}
