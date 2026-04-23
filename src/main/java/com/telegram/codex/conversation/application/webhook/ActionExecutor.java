package com.telegram.codex.conversation.application.webhook;

import com.telegram.codex.conversation.application.port.out.MediaGroupBufferPort;
import com.telegram.codex.conversation.application.job.JobSchedulerService;
import com.telegram.codex.conversation.application.webhook.action.ActionHandlerRegistry;
import com.telegram.codex.conversation.domain.Decision;
import com.telegram.codex.integration.telegram.application.TelegramWebhookHandler;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class ActionExecutor {

    private final ActionHandlerRegistry actionHandlerRegistry;
    private final MediaGroupBufferPort mediaGroupStore;
    private final JobSchedulerService jobSchedulerService;

    public ActionExecutor(
        ActionHandlerRegistry actionHandlerRegistry,
        MediaGroupBufferPort mediaGroupStore,
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
        MediaGroupBufferPort.EnqueueResult result = mediaGroupStore.enqueue(message, waitDuration.toMillis() / 1000.0);
        jobSchedulerService.scheduleMediaGroupFlush(result.key(), result.deadlineAt(), waitDuration);
        return TelegramWebhookHandler.DEFERRED;
    }
}
