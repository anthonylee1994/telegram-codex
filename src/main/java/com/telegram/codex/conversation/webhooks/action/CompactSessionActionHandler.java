package com.telegram.codex.conversation.webhooks.action;

import com.telegram.codex.constants.ConversationConstants;
import com.telegram.codex.conversation.session.SessionCompactResult;
import com.telegram.codex.conversation.session.SessionService;
import com.telegram.codex.conversation.session.SessionSnapshot;
import com.telegram.codex.conversation.updates.ProcessedUpdateFlow;
import com.telegram.codex.conversation.webhooks.Decision;
import com.telegram.codex.jobs.JobSchedulerService;
import com.telegram.codex.telegram.CompactResultSender;
import com.telegram.codex.telegram.TelegramClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CompactSessionActionHandler implements ActionHandler {

    private final JobSchedulerService jobSchedulerService;
    private final SessionService sessionService;
    private final CompactResultSender compactResultSender;
    private final TelegramClient telegramClient;
    private final ProcessedUpdateFlow processedUpdateFlow;

    public CompactSessionActionHandler(
        JobSchedulerService jobSchedulerService,
        SessionService sessionService,
        CompactResultSender compactResultSender,
        TelegramClient telegramClient,
        ProcessedUpdateFlow processedUpdateFlow
    ) {
        this.jobSchedulerService = jobSchedulerService;
        this.sessionService = sessionService;
        this.compactResultSender = compactResultSender;
        this.telegramClient = telegramClient;
        this.processedUpdateFlow = processedUpdateFlow;
    }

    @Override
    public Decision.Action handlesAction() {
        return Decision.Action.COMPACT_SESSION;
    }

    @Override
    public void execute(Decision decision, Map<String, Object> update) {
        SessionSnapshot snapshot = sessionService.snapshot(decision.message().chatId());
        if (!snapshot.active()) {
            compactResultSender.send(decision.message().chatId(), SessionCompactResult.missingSession());
            processedUpdateFlow.markProcessed(decision.message());
            return;
        }
        if (snapshot.messageCount() < ConversationConstants.MIN_TRANSCRIPT_SIZE_FOR_COMPACT) {
            compactResultSender.send(decision.message().chatId(), SessionCompactResult.tooShort(snapshot.messageCount()));
            processedUpdateFlow.markProcessed(decision.message());
            return;
        }
        jobSchedulerService.enqueueSessionCompact(decision.message().chatId());
        telegramClient.sendMessage(decision.message().chatId(), decision.responseText(), List.of(), true);
        processedUpdateFlow.markProcessed(decision.message());
    }
}
