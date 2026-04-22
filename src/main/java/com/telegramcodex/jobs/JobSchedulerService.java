package com.telegramcodex.jobs;

import com.telegramcodex.conversation.ConversationService;
import com.telegramcodex.conversation.MediaGroupStore;
import com.telegramcodex.conversation.ReplyGenerationFlow;
import com.telegramcodex.telegram.InboundMessage;
import com.telegramcodex.telegram.InboundMessageProcessor;
import com.telegramcodex.telegram.SummaryResultSender;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class JobSchedulerService {

    private final ConversationService conversationService;
    private final MediaGroupStore mediaGroupStore;
    private final ObjectProvider<InboundMessageProcessor> inboundMessageProcessorProvider;
    private final ReplyGenerationFlow replyGenerationFlow;
    private final ScheduledExecutorService scheduledExecutorService;
    private final SummaryResultSender summaryResultSender;
    private final ExecutorService taskExecutor;

    public JobSchedulerService(
        ConversationService conversationService,
        MediaGroupStore mediaGroupStore,
        ObjectProvider<InboundMessageProcessor> inboundMessageProcessorProvider,
        ReplyGenerationFlow replyGenerationFlow,
        SummaryResultSender summaryResultSender
    ) {
        this.conversationService = conversationService;
        this.mediaGroupStore = mediaGroupStore;
        this.inboundMessageProcessorProvider = inboundMessageProcessorProvider;
        this.replyGenerationFlow = replyGenerationFlow;
        this.summaryResultSender = summaryResultSender;
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

    public void enqueueSessionSummary(String chatId) {
        taskExecutor.execute(() -> {
            var result = conversationService.summarizeSession(chatId);
            summaryResultSender.send(chatId, result);
        });
    }

    @PreDestroy
    public void shutdown() {
        scheduledExecutorService.shutdown();
        taskExecutor.shutdown();
    }

    private void flushMediaGroup(String key, long expectedDeadlineAt) {
        MediaGroupStore.FlushResult result = mediaGroupStore.flush(key, expectedDeadlineAt);
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
