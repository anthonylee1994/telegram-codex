package com.telegram.codex.integration.telegram.application.webhook;

import com.telegram.codex.conversation.application.JobSchedulerService;
import com.telegram.codex.conversation.infrastructure.MediaGroupBufferRepository;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class InboundMessageProcessor {

    private final UnsupportedMessageHandler unsupportedMessageHandler;
    private final DuplicateUpdateHandler duplicateUpdateHandler;
    private final TelegramCommandHandler telegramCommandHandler;
    private final ReplyRequestGuard replyRequestGuard;
    private final MediaGroupBufferRepository mediaGroupStore;
    private final JobSchedulerService jobSchedulerService;

    public InboundMessageProcessor(
        UnsupportedMessageHandler unsupportedMessageHandler,
        DuplicateUpdateHandler duplicateUpdateHandler,
        TelegramCommandHandler telegramCommandHandler,
        ReplyRequestGuard replyRequestGuard,
        MediaGroupBufferRepository mediaGroupStore,
        JobSchedulerService jobSchedulerService
    ) {
        this.unsupportedMessageHandler = unsupportedMessageHandler;
        this.duplicateUpdateHandler = duplicateUpdateHandler;
        this.telegramCommandHandler = telegramCommandHandler;
        this.replyRequestGuard = replyRequestGuard;
        this.mediaGroupStore = mediaGroupStore;
        this.jobSchedulerService = jobSchedulerService;
    }

    public void process(InboundMessage message) {
        process(message, null);
    }

    public void process(InboundMessage message, Map<String, Object> update) {
        if (unsupportedMessageHandler.handle(message, update)) {
            return;
        }
        if (duplicateUpdateHandler.handle(message)) {
            return;
        }
        if (telegramCommandHandler.handle(message)) {
            return;
        }
        if (!replyRequestGuard.allow(message)) {
            return;
        }
        jobSchedulerService.enqueueReplyGeneration(message);
    }

    public void deferMediaGroup(InboundMessage message, Duration waitDuration) {
        MediaGroupBufferRepository.EnqueueResult result = mediaGroupStore.enqueue(message, waitDuration.toMillis() / 1000.0);
        jobSchedulerService.scheduleMediaGroupFlush(result.key(), result.deadlineAt(), waitDuration);
    }
}
