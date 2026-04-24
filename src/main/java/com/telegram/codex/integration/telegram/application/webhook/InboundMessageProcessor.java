package com.telegram.codex.integration.telegram.application.webhook;

import com.telegram.codex.conversation.application.JobSchedulerService;
import com.telegram.codex.conversation.infrastructure.MediaGroupBufferRepository;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import com.telegram.codex.integration.telegram.domain.webhook.TelegramUpdate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class InboundMessageProcessor {

    private final UnsupportedMessageHandler unsupportedMessageHandler;
    private final DuplicateUpdateHandler duplicateUpdateHandler;
    private final TelegramCommandHandler telegramCommandHandler;
    private final ReplyRequestGuard replyRequestGuard;
    private final MediaGroupBufferRepository mediaGroupStore;
    private final JobSchedulerService jobSchedulerService;
    private final List<ProcessingStep> processingSteps;

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
        // 順序好重要：先擋 unsupported / duplicate，再處理 command，最後先落一般 reply flow。
        this.processingSteps = List.of(
            this::handleUnsupportedMessage,
            this::handleDuplicateUpdate,
            this::handleTelegramCommand,
            this::handleBlockedReplyRequest
        );
    }

    public void process(InboundMessage message) {
        process(message, null);
    }

    public void process(InboundMessage message, TelegramUpdate update) {
        if (handledByProcessingStep(message, update)) {
            return;
        }
        jobSchedulerService.enqueueReplyGeneration(message);
    }

    public void deferMediaGroup(InboundMessage message, Duration waitDuration) {
        MediaGroupBufferRepository.EnqueueResult result = mediaGroupStore.enqueue(message, waitDuration.toMillis() / 1000.0);
        jobSchedulerService.scheduleMediaGroupFlush(result.key(), result.deadlineAt(), waitDuration);
    }

    private boolean handledByProcessingStep(InboundMessage message, TelegramUpdate update) {
        for (ProcessingStep step : processingSteps) {
            if (step.handle(message, update)) {
                return true;
            }
        }
        return false;
    }

    private boolean handleUnsupportedMessage(InboundMessage message, TelegramUpdate update) {
        return unsupportedMessageHandler.handle(message, update);
    }

    private boolean handleDuplicateUpdate(InboundMessage message, TelegramUpdate update) {
        return duplicateUpdateHandler.handle(message);
    }

    private boolean handleTelegramCommand(InboundMessage message, TelegramUpdate update) {
        return telegramCommandHandler.handle(message);
    }

    private boolean handleBlockedReplyRequest(InboundMessage message, TelegramUpdate update) {
        return !replyRequestGuard.allow(message);
    }

    @FunctionalInterface
    private interface ProcessingStep {
        boolean handle(InboundMessage message, TelegramUpdate update);
    }
}
