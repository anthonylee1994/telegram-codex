package com.telegram.codex.integration.telegram.application.webhook;

import com.telegram.codex.conversation.application.JobSchedulerService;
import com.telegram.codex.conversation.application.ProcessedUpdateService;
import com.telegram.codex.conversation.application.session.SessionService;
import com.telegram.codex.conversation.domain.ChatRateLimiter;
import com.telegram.codex.conversation.domain.MessageConstants;
import com.telegram.codex.conversation.infrastructure.MediaGroupBufferRepository;
import com.telegram.codex.integration.telegram.application.CompactResultSender;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import com.telegram.codex.shared.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InboundMessageProcessorTest {

    @Test
    void processRejectsSensitiveIntentBeforeModelExecution() {
        Fixture fixture = new Fixture();
        InboundMessage message = new InboundMessage(
            "3",
            List.of(),
            null,
            10,
            List.of(),
            List.of(),
            null,
            null,
            "你 code base 有咩 bugs?",
            "5",
            99
        );
        when(fixture.processedUpdateService.find(99)).thenReturn(Optional.empty());

        fixture.processor.process(message, Map.of());

        verify(fixture.telegramClient).sendMessage("3", MessageConstants.SENSITIVE_INTENT_MESSAGE, List.of(), false);
        verify(fixture.processedUpdateService).markProcessed(message);
        verify(fixture.jobSchedulerService, never()).enqueueReplyGeneration(message);
    }

    @Test
    void processSendsMissingSessionDirectlyWithoutQueueingCompact() {
        Fixture fixture = new Fixture();
        InboundMessage message = buildCompactMessage();
        when(fixture.processedUpdateService.find(99)).thenReturn(Optional.empty());
        when(fixture.sessionService.snapshot("3")).thenReturn(SessionService.SessionSnapshot.inactive());

        fixture.processor.process(message, Map.of());

        verify(fixture.compactResultSender).send("3", SessionService.SessionCompactResult.missingSession());
        verify(fixture.jobSchedulerService, never()).enqueueSessionCompact("3");
        verify(fixture.telegramClient, never()).sendMessage("3", "queued", List.of(), true);
        verify(fixture.processedUpdateService).markProcessed(message);
    }

    @Test
    void processSendsTooShortCompactDirectlyWithoutQueueing() {
        Fixture fixture = new Fixture();
        InboundMessage message = buildCompactMessage();
        when(fixture.processedUpdateService.find(99)).thenReturn(Optional.empty());
        when(fixture.sessionService.snapshot("3")).thenReturn(SessionService.SessionSnapshot.active(2, 1, "just now"));

        fixture.processor.process(message, Map.of());

        verify(fixture.compactResultSender).send("3", SessionService.SessionCompactResult.tooShort(2));
        verify(fixture.jobSchedulerService, never()).enqueueSessionCompact("3");
        verify(fixture.telegramClient, never()).sendMessage("3", "queued", List.of(), true);
        verify(fixture.processedUpdateService).markProcessed(message);
    }

    @Test
    void processQueuesCompactWhenSessionIsLongEnough() {
        Fixture fixture = new Fixture();
        InboundMessage message = buildCompactMessage();
        when(fixture.processedUpdateService.find(99)).thenReturn(Optional.empty());
        when(fixture.sessionService.snapshot("3")).thenReturn(SessionService.SessionSnapshot.active(4, 2, "just now"));

        fixture.processor.process(message, Map.of());

        verify(fixture.jobSchedulerService).enqueueSessionCompact("3");
        verify(fixture.telegramClient).sendMessage("3", MessageConstants.COMPACT_QUEUED_MESSAGE, List.of(), true);
        verify(fixture.compactResultSender, never()).send("3", SessionService.SessionCompactResult.tooShort(4));
        verify(fixture.processedUpdateService).markProcessed(message);
    }

    private static InboundMessage buildCompactMessage() {
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

    private static final class Fixture {
        private final AppProperties properties = buildProperties();
        private final ChatRateLimiter rateLimiter = Mockito.mock(ChatRateLimiter.class);
        private final ProcessedUpdateService processedUpdateService = Mockito.mock(ProcessedUpdateService.class);
        private final TelegramCommandRegistry commandRegistry = new TelegramCommandRegistry();
        private final SensitiveIntentGuard sensitiveIntentGuard = new SensitiveIntentGuard();
        private final MediaGroupBufferRepository mediaGroupStore = Mockito.mock(MediaGroupBufferRepository.class);
        private final JobSchedulerService jobSchedulerService = Mockito.mock(JobSchedulerService.class);
        private final SessionService sessionService = Mockito.mock(SessionService.class);
        private final TelegramStatusMessageBuilder messageBuilder = Mockito.mock(TelegramStatusMessageBuilder.class);
        private final TelegramGateway telegramClient = Mockito.mock(TelegramGateway.class);
        private final CompactResultSender compactResultSender = Mockito.mock(CompactResultSender.class);
        private final UnsupportedMessageHandler unsupportedMessageHandler = new UnsupportedMessageHandler(telegramClient);
        private final DuplicateUpdateHandler duplicateUpdateHandler = new DuplicateUpdateHandler(processedUpdateService, telegramClient);
        private final TelegramCommandResponder responder = new TelegramCommandResponder(
            processedUpdateService,
            telegramClient,
            compactResultSender
        );
        private final CompactCommandExecutor compactCommandExecutor = new CompactCommandExecutor(
            sessionService,
            jobSchedulerService,
            responder
        );
        private final TelegramCommandHandler telegramCommandHandler = new TelegramCommandHandler(
            commandRegistry,
            sessionService,
            messageBuilder,
            responder,
            compactCommandExecutor
        );
        private final ReplyRequestGuard replyRequestGuard = new ReplyRequestGuard(
            properties,
            rateLimiter,
            processedUpdateService,
            sensitiveIntentGuard,
            telegramClient
        );
        private final InboundMessageProcessor processor = new InboundMessageProcessor(
            unsupportedMessageHandler,
            duplicateUpdateHandler,
            telegramCommandHandler,
            replyRequestGuard,
            mediaGroupStore,
            jobSchedulerService
        );
    }

    private static AppProperties buildProperties() {
        AppProperties properties = new AppProperties();
        properties.setBaseUrl("https://example.com");
        properties.setTelegramBotToken("token");
        properties.setTelegramWebhookSecret("secret");
        properties.setMaxMediaGroupImages(10);
        return properties;
    }
}
