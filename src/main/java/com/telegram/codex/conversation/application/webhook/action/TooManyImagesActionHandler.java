package com.telegram.codex.conversation.application.webhook.action;

import com.telegram.codex.conversation.application.update.ProcessedUpdateFlow;
import com.telegram.codex.conversation.domain.Decision;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TooManyImagesActionHandler implements ActionHandler {

    private final TelegramGateway telegramClient;
    private final ProcessedUpdateFlow processedUpdateFlow;

    public TooManyImagesActionHandler(TelegramGateway telegramClient, ProcessedUpdateFlow processedUpdateFlow) {
        this.telegramClient = telegramClient;
        this.processedUpdateFlow = processedUpdateFlow;
    }

    @Override
    public Decision.Action handlesAction() {
        return Decision.Action.TOO_MANY_IMAGES;
    }

    @Override
    public void execute(Decision decision, Map<String, Object> update) {
        telegramClient.sendMessage(decision.message().chatId(), decision.responseText(), List.of(), false);
        processedUpdateFlow.markProcessed(decision.message());
    }
}
