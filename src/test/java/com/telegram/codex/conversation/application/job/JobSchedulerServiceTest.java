package com.telegram.codex.conversation.application.job;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telegram.codex.conversation.application.JobSchedulerService;
import com.telegram.codex.conversation.application.reply.ReplyGenerationService;
import com.telegram.codex.conversation.application.session.SessionService;
import com.telegram.codex.conversation.infrastructure.MediaGroupBufferRepository;
import com.telegram.codex.integration.telegram.application.CompactResultSender;
import com.telegram.codex.integration.telegram.application.webhook.InboundMessageProcessor;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import java.time.Duration;
import java.util.List;
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
        ReplyGenerationService replyGenerationService = Mockito.mock(ReplyGenerationService.class);
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(replyGenerationService).handle(any());

        service = new JobSchedulerService(
            Mockito.mock(MediaGroupBufferRepository.class),
            mockProcessorProvider(),
            replyGenerationService,
            Mockito.mock(SessionService.class),
            Mockito.mock(CompactResultSender.class)
        );

        InboundMessage message = buildMessage();
        service.enqueueReplyGeneration(message);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        verify(replyGenerationService).handle(message);
    }

    @Test
    void enqueueSessionCompactRunsInsideWebProcess() throws Exception {
        SessionService sessionService = Mockito.mock(SessionService.class);
        CompactResultSender compactResultSender = Mockito.mock(CompactResultSender.class);
        CountDownLatch latch = new CountDownLatch(1);
        SessionService.SessionCompactResult result = SessionService.SessionCompactResult.ok(6, "sum");
        when(sessionService.compact("3")).thenReturn(result);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(compactResultSender).send(eq("3"), eq(result));

        service = new JobSchedulerService(
            Mockito.mock(MediaGroupBufferRepository.class),
            mockProcessorProvider(),
            Mockito.mock(ReplyGenerationService.class),
            sessionService,
            compactResultSender
        );

        service.enqueueSessionCompact("3");

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        verify(sessionService).compact("3");
        verify(compactResultSender).send("3", result);
    }

    @Test
    void scheduleMediaGroupFlushProcessesReadyMessage() throws Exception {
        MediaGroupBufferRepository mediaGroupStore = Mockito.mock(MediaGroupBufferRepository.class);
        InboundMessageProcessor inboundMessageProcessor = Mockito.mock(InboundMessageProcessor.class);
        CountDownLatch latch = new CountDownLatch(1);
        InboundMessage message = buildMessage();
        when(mediaGroupStore.flush("3:group", 123L)).thenReturn(MediaGroupBufferRepository.FlushResult.ready(message));
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(inboundMessageProcessor).process(message);

        service = new JobSchedulerService(
            mediaGroupStore,
            mockProcessorProvider(inboundMessageProcessor),
            Mockito.mock(ReplyGenerationService.class),
            Mockito.mock(SessionService.class),
            Mockito.mock(CompactResultSender.class)
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
            List.of(),
            List.of(),
            null,
            null,
            "hello",
            "5",
            99
        );
    }
}
