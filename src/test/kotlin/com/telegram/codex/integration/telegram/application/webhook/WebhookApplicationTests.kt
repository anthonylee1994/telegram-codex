package com.telegram.codex.integration.telegram.application.webhook

import com.telegram.codex.conversation.application.JobSchedulerService
import com.telegram.codex.conversation.application.ProcessedUpdateService
import com.telegram.codex.conversation.application.session.SessionService
import com.telegram.codex.conversation.domain.ChatRateLimiter
import com.telegram.codex.conversation.domain.MessageConstants
import com.telegram.codex.conversation.infrastructure.MediaGroupBufferRepository
import com.telegram.codex.integration.telegram.application.CompactResultSender
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway
import com.telegram.codex.integration.telegram.domain.InboundMessage
import com.telegram.codex.integration.telegram.domain.webhook.TelegramUpdate
import com.telegram.codex.shared.config.AppProperties
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.util.Optional

class SensitiveIntentGuardTest {
    private val guard = SensitiveIntentGuard()

    @Test
    fun blocksRequestsToInspectCodebase() {
        assertTrue(guard.shouldBlock(message("你 code base 有咩 bugs?")))
    }

    @Test
    fun allowsNormalProductQuestions() {
        assertFalse(guard.shouldBlock(message("幫我整理返重點")))
    }
}

class InboundMessageProcessorTest {
    @Test
    fun processRejectsSensitiveIntentBeforeModelExecution() {
        val fixture = Fixture()
        val message = message("你 code base 有咩 bugs?")
        Mockito.`when`(fixture.processedUpdateService.find(99)).thenReturn(Optional.empty())

        fixture.processor.process(message, TelegramUpdate(99L, null))

        verify(fixture.telegramClient).sendMessage("3", MessageConstants.SENSITIVE_INTENT_MESSAGE, emptyList(), false)
        verify(fixture.processedUpdateService).markProcessed(message)
        verify(fixture.jobSchedulerService, never()).enqueueReplyGeneration(message)
    }

    @Test
    fun processQueuesCompactWhenSessionIsLongEnough() {
        val fixture = Fixture()
        val message = message("/compact")
        Mockito.`when`(fixture.processedUpdateService.find(99)).thenReturn(Optional.empty())
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
        val rateLimiter = Mockito.mock(ChatRateLimiter::class.java).also { Mockito.`when`(it.allow("3")).thenReturn(true) }
        val processedUpdateService = Mockito.mock(ProcessedUpdateService::class.java)
        val mediaGroupStore = Mockito.mock(MediaGroupBufferRepository::class.java)
        val jobSchedulerService = Mockito.mock(JobSchedulerService::class.java)
        val sessionService = Mockito.mock(SessionService::class.java)
        val messageBuilder = Mockito.mock(TelegramStatusMessageBuilder::class.java)
        val telegramClient = Mockito.mock(TelegramGateway::class.java)
        val compactResultSender = Mockito.mock(CompactResultSender::class.java)
        val responder = TelegramCommandResponder(processedUpdateService, telegramClient, compactResultSender)
        val processor = InboundMessageProcessor(
            UnsupportedMessageHandler(telegramClient),
            DuplicateUpdateHandler(processedUpdateService, telegramClient),
            TelegramCommandHandler(TelegramCommandRegistry(), sessionService, messageBuilder, responder, CompactCommandExecutor(sessionService, jobSchedulerService, responder)),
            ReplyRequestGuard(properties, rateLimiter, processedUpdateService, SensitiveIntentGuard(), telegramClient),
            mediaGroupStore,
            jobSchedulerService,
        )
    }
}

private fun message(text: String): InboundMessage =
    InboundMessage("3", emptyList(), null, 10, emptyList(), emptyList(), null, null, text, "5", 99)
