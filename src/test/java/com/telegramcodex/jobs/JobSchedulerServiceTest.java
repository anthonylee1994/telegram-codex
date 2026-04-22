package com.telegramcodex.jobs;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telegramcodex.conversation.ConversationService;
import com.telegramcodex.conversation.MediaGroupStore;
import com.telegramcodex.conversation.ReplyGenerationFlow;
import com.telegramcodex.telegram.InboundMessage;
import com.telegramcodex.telegram.InboundMessageProcessor;
import com.telegramcodex.telegram.SummaryResultSender;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

class JobSchedulerServiceTest {

    private JobSchedulerService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    @Test
    void enqueueReplyGenerationRunsInsideWebProcess() throws Exception {
        ReplyGenerationFlow replyGenerationFlow = Mockito.mock(ReplyGenerationFlow.class);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(replyGenerationFlow).call(any());

        service = new JobSchedulerService(
            Mockito.mock(ConversationService.class),
            Mockito.mock(MediaGroupStore.class),
            mockProcessorProvider(),
            replyGenerationFlow,
            Mockito.mock(SummaryResultSender.class)
        );

        InboundMessage message = buildMessage();
        service.enqueueReplyGeneration(message);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        verify(replyGenerationFlow).call(message);
    }

    @Test
    void enqueueSessionSummaryRunsInsideWebProcess() throws Exception {
        ConversationService conversationService = Mockito.mock(ConversationService.class);
        SummaryResultSender summaryResultSender = Mockito.mock(SummaryResultSender.class);
        CountDownLatch latch = new CountDownLatch(1);
        Map<String, Object> result = Map.of("status", "ok", "summary_text", "sum");
        when(conversationService.summarizeSession("3")).thenReturn(result);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(summaryResultSender).send(eq("3"), eq(result));

        service = new JobSchedulerService(
            conversationService,
            Mockito.mock(MediaGroupStore.class),
            mockProcessorProvider(),
            Mockito.mock(ReplyGenerationFlow.class),
            summaryResultSender
        );

        service.enqueueSessionSummary("3");

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        verify(conversationService).summarizeSession("3");
        verify(summaryResultSender).send("3", result);
    }

    @Test
    void scheduleMediaGroupFlushProcessesReadyMessage() throws Exception {
        MediaGroupStore mediaGroupStore = Mockito.mock(MediaGroupStore.class);
        InboundMessageProcessor inboundMessageProcessor = Mockito.mock(InboundMessageProcessor.class);
        CountDownLatch latch = new CountDownLatch(1);
        InboundMessage message = buildMessage();
        when(mediaGroupStore.flush("3:group", 123L)).thenReturn(MediaGroupStore.FlushResult.ready(message));
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(inboundMessageProcessor).process(message);

        service = new JobSchedulerService(
            Mockito.mock(ConversationService.class),
            mediaGroupStore,
            mockProcessorProvider(inboundMessageProcessor),
            Mockito.mock(ReplyGenerationFlow.class),
            Mockito.mock(SummaryResultSender.class)
        );

        service.scheduleMediaGroupFlush("3:group", 123L, Duration.ofMillis(10));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        verify(mediaGroupStore).flush("3:group", 123L);
        verify(inboundMessageProcessor).process(message);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<InboundMessageProcessor> mockProcessorProvider(InboundMessageProcessor... processor) {
        InboundMessageProcessor value = processor.length == 0 ? Mockito.mock(InboundMessageProcessor.class) : processor[0];
        ObjectProvider<InboundMessageProcessor> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(value);
        return provider;
    }

    private InboundMessage buildMessage() {
        return new InboundMessage(
            "3",
            List.of(),
            null,
            10,
            null,
            List.of(),
            List.of(),
            null,
            null,
            null,
            null,
            null,
            "hello",
            null,
            null,
            "5",
            99
        );
    }
}
