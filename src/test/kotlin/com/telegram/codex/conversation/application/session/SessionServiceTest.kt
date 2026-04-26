package com.telegram.codex.conversation.application.session

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.telegram.codex.conversation.domain.session.ChatSessionRecord
import com.telegram.codex.conversation.domain.session.Transcript
import com.telegram.codex.conversation.infrastructure.memory.ChatMemoryRepository
import com.telegram.codex.conversation.infrastructure.session.ChatSessionRepository
import com.telegram.codex.conversation.infrastructure.session.CodexSessionCompactClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.verify
import java.util.Optional

class SessionServiceTest {
    @Test
    fun snapshotReturnsInactiveWhenNoSession() {
        val repository = Mockito.mock(ChatSessionRepository::class.java)
        Mockito.`when`(repository.findActive("3")).thenReturn(Optional.empty())

        val snapshot = SessionService(repository, Mockito.mock(CodexSessionCompactClient::class.java), mapper(), Mockito.mock(ChatMemoryRepository::class.java)).snapshot("3")

        assertFalse(snapshot.active)
    }

    @Test
    fun compactReturnsOkAndPersistsCompactTranscript() {
        val repository = Mockito.mock(ChatSessionRepository::class.java)
        val compactClient = Mockito.mock(CodexSessionCompactClient::class.java)
        val objectMapper = mapper()
        val conversationState = Transcript.empty()
            .append("user", "a")
            .append("assistant", "b")
            .append("user", "c")
            .append("assistant", "d")
            .toConversationState(objectMapper)
        Mockito.`when`(repository.findActive("3")).thenReturn(Optional.of(ChatSessionRecord("3", conversationState, System.currentTimeMillis())))
        Mockito.`when`(compactClient.compact(any())).thenReturn("sum")

        val result = SessionService(repository, compactClient, objectMapper, Mockito.mock(ChatMemoryRepository::class.java)).compact("3")

        assertEquals(SessionService.SessionCompactResult.Status.OK, result.status)
        assertEquals(4, result.originalMessageCount)
        assertEquals("sum", result.compactText)
        verify(repository).persist(Mockito.anyString(), Mockito.anyString())
    }

    @Test
    fun snapshotReturnsMessageStatsWhenSessionExists() {
        val repository = Mockito.mock(ChatSessionRepository::class.java)
        val objectMapper = mapper()
        val conversationState = Transcript.empty().append("user", "a").append("assistant", "b").append("user", "c").toConversationState(objectMapper)
        Mockito.`when`(repository.findActive("3")).thenReturn(Optional.of(ChatSessionRecord("3", conversationState, System.currentTimeMillis())))

        val snapshot = SessionService(repository, Mockito.mock(CodexSessionCompactClient::class.java), objectMapper, Mockito.mock(ChatMemoryRepository::class.java)).snapshot("3")

        assertTrue(snapshot.active)
        assertEquals(3, snapshot.messageCount)
        assertEquals(2, snapshot.turnCount)
    }

    private fun mapper(): ObjectMapper = ObjectMapper().registerKotlinModule()
}
