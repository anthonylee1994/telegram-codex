package com.telegram.codex.conversation.webhooks.action;

import com.telegram.codex.conversation.updates.ProcessedUpdateFlow;
import com.telegram.codex.conversation.webhooks.Decision;
import com.telegram.codex.jobs.JobSchedulerService;
import com.telegram.codex.telegram.TelegramClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SummarizeSessionActionHandler implements ActionHandler {

    private final JobSchedulerService jobSchedulerService;
    private final TelegramClient telegramClient;
    private final ProcessedUpdateFlow processedUpdateFlow;

    public SummarizeSessionActionHandler(
        JobSchedulerService jobSchedulerService,
        TelegramClient telegramClient,
        ProcessedUpdateFlow processedUpdateFlow
    ) {
        this.jobSchedulerService = jobSchedulerService;
        this.telegramClient = telegramClient;
        this.processedUpdateFlow = processedUpdateFlow;
    }

    @Override
    public Decision.Action handlesAction() {
        return Decision.Action.SUMMARIZE_SESSION;
    }

    @Override
    public void execute(Decision decision, Map<String, Object> update) {
        jobSchedulerService.enqueueSessionSummary(decision.message().chatId());
        telegramClient.sendMessage(decision.message().chatId(), decision.responseText(), List.of(), true);
        processedUpdateFlow.markProcessed(decision.message());
    }
}
