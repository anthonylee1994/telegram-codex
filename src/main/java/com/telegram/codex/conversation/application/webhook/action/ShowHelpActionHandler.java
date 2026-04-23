package com.telegram.codex.conversation.application.webhook.action;

import com.telegram.codex.conversation.application.update.ProcessedUpdateFlow;
import com.telegram.codex.conversation.domain.Decision;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import com.telegram.codex.conversation.domain.MessageConstants;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ShowHelpActionHandler implements ActionHandler {

    private final TelegramGateway telegramClient;
    private final ProcessedUpdateFlow processedUpdateFlow;

    public ShowHelpActionHandler(TelegramGateway telegramClient, ProcessedUpdateFlow processedUpdateFlow) {
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
