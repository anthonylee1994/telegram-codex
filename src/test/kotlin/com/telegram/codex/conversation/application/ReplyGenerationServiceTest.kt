package com.telegram.codex.conversation.application

import com.telegram.codex.conversation.domain.ChatMemoryRecord
import com.telegram.codex.conversation.domain.ChatSessionRecord
import com.telegram.codex.conversation.infrastructure.ChatMemoryRepository
import com.telegram.codex.conversation.infrastructure.ChatSessionRepository
import com.telegram.codex.conversation.infrastructure.CodexMemoryClient
import com.telegram.codex.integration.telegram.application.TelegramGateway
import com.telegram.codex.integration.telegram.domain.InboundMessage
import com.telegram.codex.integration.telegram.domain.TelegramBotCommand
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.verify
import java.nio.file.Path

class ReplyGenerationServiceTest {
    @Test
    fun handleUsesStoredConversationState() {
        val cliClient = Mockito.mock(ReplyGenerationGateway::class.java)
        val memoryClient = Mockito.mock(CodexMemoryClient::class.java)
        val processedUpdateService = Mockito.mock(ProcessedUpdateService::class.java)
        val sessionService = Mockito.mock(SessionService::class.java)
        val telegramGateway = FakeTelegramGateway()
        val attachmentDownloader = Mockito.mock(AttachmentDownloader::class.java)
        Mockito.`when`(cliClient.generateReply(Mockito.anyString(), Mockito.any(), Mockito.anyList(), Mockito.any(), Mockito.any()))
            .thenReturn(ReplyResult("next-state", listOf("a", "b", "c"), "reply"))
        val sessionRepository = Mockito.mock(ChatSessionRepository::class.java)
        Mockito.`when`(sessionRepository.findActive("3")).thenReturn(ChatSessionRecord("3", "state", System.currentTimeMillis()))
        val memoryRepository = Mockito.mock(ChatMemoryRepository::class.java)
        Mockito.`when`(memoryRepository.find("3")).thenReturn(ChatMemoryRecord("3", "記憶", System.currentTimeMillis()))
        Mockito.`when`(memoryClient.merge("記憶", "你好", "reply")).thenReturn("記憶")
        Mockito.`when`(attachmentDownloader.downloadImages(emptyList())).thenReturn(emptyList())

        val service = ReplyGenerationService(cliClient, sessionRepository, memoryRepository, memoryClient, processedUpdateService, sessionService, telegramGateway, attachmentDownloader)
        val message = InboundMessage("3", emptyList(), null, 10, emptyList(), emptyList(), null, "你好", "5", 99)

        service.handle(message)

        verify(processedUpdateService).pruneIfNeeded()
        verify(processedUpdateService).savePendingReply(99L, "3", 10L, ReplyResult("next-state", listOf("a", "b", "c"), "reply"))
        assertEquals(FakeTelegramGateway.SentMessage("3", "reply", listOf("a", "b", "c"), false), telegramGateway.sentMessage)
        verify(sessionService).persistConversationState("3", "next-state")
        verify(processedUpdateService).markProcessed(99L, "3", 10L)
    }

    private class FakeTelegramGateway : TelegramGateway {
        var sentMessage: SentMessage? = null

        override fun downloadFileToTemp(fileId: String): Path = throw UnsupportedOperationException()
        override fun sendMessage(chatId: String, text: String?, suggestedReplies: List<String>, removeKeyboard: Boolean) {
            sentMessage = SentMessage(chatId, text, suggestedReplies, removeKeyboard)
        }
        override fun <T> withTypingStatus(chatId: String, action: () -> T): T = action()
        override fun setWebhook(url: String, secretToken: String) = Unit
        override fun setMyCommands(commands: List<TelegramBotCommand>) = Unit

        data class SentMessage(val chatId: String, val text: String?, val suggestedReplies: List<String>, val removeKeyboard: Boolean)
    }
}
