package com.telegram.codex.conversation.application.job;

import com.telegram.codex.conversation.application.port.out.MediaGroupBufferPort;
import com.telegram.codex.conversation.application.reply.ReplyGenerationFlow;
import com.telegram.codex.conversation.application.session.SessionService;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import com.telegram.codex.integration.telegram.application.InboundMessageProcessor;
import com.telegram.codex.integration.telegram.application.CompactResultSender;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class JobSchedulerService {

    private final MediaGroupBufferPort mediaGroupStore;
    private final ObjectProvider<InboundMessageProcessor> inboundMessageProcessorProvider;
    private final ReplyGenerationFlow replyGenerationFlow;
    private final ScheduledExecutorService scheduledExecutorService;
    private final SessionService sessionService;
    private final CompactResultSender compactResultSender;
    private final ExecutorService taskExecutor;

    public JobSchedulerService(
        MediaGroupBufferPort mediaGroupStore,
        ObjectProvider<InboundMessageProcessor> inboundMessageProcessorProvider,
        ReplyGenerationFlow replyGenerationFlow,
        SessionService sessionService,
        CompactResultSender compactResultSender
    ) {
        this.mediaGroupStore = mediaGroupStore;
        this.inboundMessageProcessorProvider = inboundMessageProcessorProvider;
        this.replyGenerationFlow = replyGenerationFlow;
        this.sessionService = sessionService;
        this.compactResultSender = compactResultSender;
        this.taskExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
            .name("media-group-scheduler-", 0)
            .daemon(true)
            .factory());
    }

    public void enqueueReplyGeneration(InboundMessage message) {
        taskExecutor.execute(() -> replyGenerationFlow.call(message));
    }

    public void scheduleMediaGroupFlush(String key, long expectedDeadlineAt, Duration waitDuration) {
        scheduledExecutorService.schedule(
            () -> taskExecutor.execute(() -> flushMediaGroup(key, expectedDeadlineAt)),
            waitDuration.toMillis(),
            TimeUnit.MILLISECONDS
        );
    }

    public void enqueueSessionCompact(String chatId) {
        taskExecutor.execute(() -> {
            var result = sessionService.compact(chatId);
            compactResultSender.send(chatId, result);
        });
    }

    @PreDestroy
    public void shutdown() {
        scheduledExecutorService.shutdown();
        taskExecutor.shutdown();
    }

    private void flushMediaGroup(String key, long expectedDeadlineAt) {
        MediaGroupBufferPort.FlushResult result = mediaGroupStore.flush(key, expectedDeadlineAt);
        switch (result.status()) {
            case "ready" -> inboundMessageProcessorProvider.getObject().process(result.message());
            case "pending" -> scheduleMediaGroupFlush(
                key,
                expectedDeadlineAt,
                Duration.ofMillis(Math.round(result.waitDurationSeconds() * 1000.0))
            );
            default -> {
            }
        }
    }
}
