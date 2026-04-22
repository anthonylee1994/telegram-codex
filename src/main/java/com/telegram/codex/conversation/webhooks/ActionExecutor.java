package com.telegram.codex.conversation.webhooks;

import com.telegram.codex.conversation.MediaGroupStore;
import com.telegram.codex.conversation.webhooks.action.ActionHandlerRegistry;
import com.telegram.codex.jobs.JobSchedulerService;
import com.telegram.codex.telegram.InboundMessage;
import com.telegram.codex.telegram.TelegramWebhookHandler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class ActionExecutor {

    private final ActionHandlerRegistry actionHandlerRegistry;
    private final MediaGroupStore mediaGroupStore;
    private final JobSchedulerService jobSchedulerService;

    public ActionExecutor(
        ActionHandlerRegistry actionHandlerRegistry,
        MediaGroupStore mediaGroupStore,
        JobSchedulerService jobSchedulerService
    ) {
        this.actionHandlerRegistry = actionHandlerRegistry;
        this.mediaGroupStore = mediaGroupStore;
        this.jobSchedulerService = jobSchedulerService;
    }

    public void call(Decision decision, Map<String, Object> update) {
        actionHandlerRegistry.execute(decision, update);
    }

    public Object deferMediaGroup(InboundMessage message, Duration waitDuration) {
        MediaGroupStore.EnqueueResult result = mediaGroupStore.enqueue(message, waitDuration.toMillis() / 1000.0);
        jobSchedulerService.scheduleMediaGroupFlush(result.key(), result.deadlineAt(), waitDuration);
        return TelegramWebhookHandler.DEFERRED;
    }
}
