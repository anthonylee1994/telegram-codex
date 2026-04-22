package com.telegram.codex.conversation.webhooks.action;

import com.telegram.codex.constants.MessageConstants;
import com.telegram.codex.conversation.updates.ProcessedUpdateFlow;
import com.telegram.codex.conversation.webhooks.Decision;
import com.telegram.codex.telegram.TelegramClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ShowHelpActionHandler implements ActionHandler {

    private final TelegramClient telegramClient;
    private final ProcessedUpdateFlow processedUpdateFlow;

    public ShowHelpActionHandler(TelegramClient telegramClient, ProcessedUpdateFlow processedUpdateFlow) {
        this.telegramClient = telegramClient;
        this.processedUpdateFlow = processedUpdateFlow;
    }

    @Override
    public Decision.Action handlesAction() {
        return Decision.Action.SHOW_HELP;
    }

    @Override
    public void execute(Decision decision, Map<String, Object> update) {
        telegramClient.sendMessage(decision.message().chatId(), MessageConstants.HELP_MESSAGE, List.of(), true);
        processedUpdateFlow.markProcessed(decision.message());
    }
}
