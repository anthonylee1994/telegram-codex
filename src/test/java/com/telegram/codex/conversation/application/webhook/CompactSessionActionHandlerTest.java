package com.telegram.codex.conversation.application.webhook;

import com.telegram.codex.conversation.application.job.JobSchedulerService;
import com.telegram.codex.conversation.application.session.SessionService;
import com.telegram.codex.conversation.application.update.ProcessedUpdateFlow;
import com.telegram.codex.conversation.application.webhook.action.CompactSessionActionHandler;
import com.telegram.codex.conversation.domain.Decision;
import com.telegram.codex.conversation.domain.session.SessionCompactResult;
import com.telegram.codex.conversation.domain.session.SessionSnapshot;
import com.telegram.codex.integration.telegram.application.CompactResultSender;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompactSessionActionHandlerTest {

    @Test
    void executeSendsTooShortDirectlyWithoutQueueing() {
        JobSchedulerService jobSchedulerService = Mockito.mock(JobSchedulerService.class);
        SessionService sessionService = Mockito.mock(SessionService.class);
        CompactResultSender compactResultSender = Mockito.mock(CompactResultSender.class);
        TelegramGateway telegramClient = Mockito.mock(TelegramGateway.class);
        ProcessedUpdateFlow processedUpdateFlow = Mockito.mock(ProcessedUpdateFlow.class);
        InboundMessage message = buildMessage();
        when(sessionService.snapshot("3")).thenReturn(SessionSnapshot.active(2, 1, "just now"));
        CompactSessionActionHandler handler = new CompactSessionActionHandler(
            jobSchedulerService,
            sessionService,
            compactResultSender,
            telegramClient,
            processedUpdateFlow
        );

        handler.execute(Decision.compactSession(message, "queued"), Map.of());

        verify(compactResultSender).send("3", SessionCompactResult.tooShort(2));
        verify(jobSchedulerService, never()).enqueueSessionCompact("3");
        verify(telegramClient, never()).sendMessage("3", "queued", List.of(), true);
        verify(processedUpdateFlow).markProcessed(message);
    }

    @Test
    void executeQueuesCompactWhenSessionIsLongEnough() {
        JobSchedulerService jobSchedulerService = Mockito.mock(JobSchedulerService.class);
        SessionService sessionService = Mockito.mock(SessionService.class);
        CompactResultSender compactResultSender = Mockito.mock(CompactResultSender.class);
        TelegramGateway telegramClient = Mockito.mock(TelegramGateway.class);
        ProcessedUpdateFlow processedUpdateFlow = Mockito.mock(ProcessedUpdateFlow.class);
        InboundMessage message = buildMessage();
        when(sessionService.snapshot("3")).thenReturn(SessionSnapshot.active(4, 2, "just now"));
        CompactSessionActionHandler handler = new CompactSessionActionHandler(
            jobSchedulerService,
            sessionService,
            compactResultSender,
            telegramClient,
            processedUpdateFlow
        );

        handler.execute(Decision.compactSession(message, "queued"), Map.of());

        verify(jobSchedulerService).enqueueSessionCompact("3");
        verify(telegramClient).sendMessage("3", "queued", List.of(), true);
        verify(compactResultSender, never()).send("3", SessionCompactResult.tooShort(4));
        verify(processedUpdateFlow).markProcessed(message);
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
            "/compact",
            "5",
            99
        );
    }
}
