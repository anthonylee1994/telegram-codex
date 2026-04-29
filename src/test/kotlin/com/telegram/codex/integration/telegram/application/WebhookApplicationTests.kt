package com.telegram.codex.integration.telegram.application

import com.telegram.codex.conversation.application.JobSchedulerService
import com.telegram.codex.conversation.application.ProcessedUpdateService
import com.telegram.codex.conversation.application.SessionService
import com.telegram.codex.conversation.domain.ChatRateLimiter
import com.telegram.codex.conversation.domain.MessageConstants
import com.telegram.codex.conversation.infrastructure.MediaGroupBufferRepository
import com.telegram.codex.integration.telegram.domain.InboundMessage
import com.telegram.codex.integration.telegram.domain.TelegramUpdate
import com.telegram.codex.shared.AppProperties
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

class InboundMessageProcessorTest {
    @Test
    fun processAllowsCodebaseIntentAfterGuardRemoval() {
        val fixture = Fixture()
        val message = message("你 code base 有咩 bugs?")
        Mockito.`when`(fixture.processedUpdateService.find(99)).thenReturn(null)
        Mockito.`when`(fixture.processedUpdateService.beginProcessing(message)).thenReturn(true)

        fixture.processor.process(message, TelegramUpdate(99L, null))

        verify(fixture.jobSchedulerService).enqueueReplyGeneration(message)
        verifyNoInteractions(fixture.telegramClient)
    }

    @Test
    fun processQueuesCompactWhenSessionIsLongEnough() {
        val fixture = Fixture()
        val message = message("/compact")
        Mockito.`when`(fixture.processedUpdateService.find(99)).thenReturn(null)
        Mockito.`when`(fixture.sessionService.snapshot("3")).thenReturn(SessionService.SessionSnapshot.active(4, 2, "just now"))

        fixture.processor.process(message, TelegramUpdate(99L, null))

        verify(fixture.jobSchedulerService).enqueueSessionCompact("3")
        verify(fixture.telegramClient).sendMessage("3", MessageConstants.COMPACT_QUEUED_MESSAGE, emptyList(), true)
        verify(fixture.processedUpdateService).markProcessed(message)
    }

    private class Fixture {
        val properties = AppProperties().apply {
            baseUrl = "https://example.com"
            telegramBotToken = "token"
            telegramWebhookSecret = "secret"
            maxMediaGroupImages = 10
        }
        val rateLimiter: ChatRateLimiter = Mockito.mock(ChatRateLimiter::class.java).also { Mockito.`when`(it.allow("3")).thenReturn(true) }
        val processedUpdateService: ProcessedUpdateService = Mockito.mock(ProcessedUpdateService::class.java)
        val mediaGroupStore: MediaGroupBufferRepository = Mockito.mock(MediaGroupBufferRepository::class.java)
        val jobSchedulerService: JobSchedulerService = Mockito.mock(JobSchedulerService::class.java)
        val sessionService: SessionService = Mockito.mock(SessionService::class.java)
        val messageBuilder: TelegramStatusMessageBuilder = Mockito.mock(TelegramStatusMessageBuilder::class.java)
        val telegramClient: TelegramGateway = Mockito.mock(TelegramGateway::class.java)
        val compactResultSender: CompactResultSender = Mockito.mock(CompactResultSender::class.java)
        val responder = TelegramCommandResponder(processedUpdateService, telegramClient, compactResultSender)
        val processor = InboundMessageProcessor(
            UnsupportedMessageHandler(telegramClient),
            DuplicateUpdateHandler(processedUpdateService, telegramClient),
            TelegramCommandHandler(TelegramCommandRegistry(), sessionService, messageBuilder, responder, CompactCommandExecutor(sessionService, jobSchedulerService, responder)),
            ReplyRequestGuard(properties, rateLimiter, processedUpdateService, telegramClient),
            mediaGroupStore,
            jobSchedulerService,
        )
    }
}

private fun message(text: String): InboundMessage =
    InboundMessage("3", emptyList(), null, 10, emptyList(), emptyList(), null, text, "5", 99)
