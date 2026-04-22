package com.telegram.codex.conversation.webhooks.action;

import com.telegram.codex.conversation.memory.MemoryService;
import com.telegram.codex.conversation.updates.ProcessedUpdateFlow;
import com.telegram.codex.conversation.webhooks.Decision;
import com.telegram.codex.telegram.TelegramClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ResetMemoryActionHandler implements ActionHandler {

    private final MemoryService memoryService;
    private final TelegramClient telegramClient;
    private final ProcessedUpdateFlow processedUpdateFlow;

    public ResetMemoryActionHandler(
        MemoryService memoryService,
        TelegramClient telegramClient,
        ProcessedUpdateFlow processedUpdateFlow
    ) {
        this.memoryService = memoryService;
        this.telegramClient = telegramClient;
        this.processedUpdateFlow = processedUpdateFlow;
    }

    @Override
    public Decision.Action handlesAction() {
        return Decision.Action.RESET_MEMORY;
    }

    @Override
    public void execute(Decision decision, Map<String, Object> update) {
        memoryService.reset(decision.message().chatId());
        telegramClient.sendMessage(decision.message().chatId(), decision.responseText(), List.of(), true);
        processedUpdateFlow.markProcessed(decision.message());
    }
}
