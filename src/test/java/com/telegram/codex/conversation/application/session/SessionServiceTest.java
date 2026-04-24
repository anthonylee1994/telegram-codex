package com.telegram.codex.conversation.application.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.conversation.domain.session.ChatSessionRecord;
import com.telegram.codex.conversation.domain.session.Transcript;
import com.telegram.codex.conversation.infrastructure.memory.ChatMemoryRepository;
import com.telegram.codex.conversation.infrastructure.session.ChatSessionRepository;
import com.telegram.codex.conversation.infrastructure.session.CodexSessionCompactClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionServiceTest {

    @Test
    void snapshotReturnsInactiveWhenNoSession() {
        ChatSessionRepository repository = Mockito.mock(ChatSessionRepository.class);
        when(repository.findActive("3")).thenReturn(Optional.empty());

        SessionService.SessionSnapshot snapshot = new SessionService(repository, Mockito.mock(CodexSessionCompactClient.class), new ObjectMapper(), Mockito.mock(ChatMemoryRepository.class)).snapshot("3");

        assertFalse(snapshot.active());
    }

    @Test
    void compactReturnsOkAndPersistsCompactTranscript() {
        ChatSessionRepository repository = Mockito.mock(ChatSessionRepository.class);
        CodexSessionCompactClient compactClient = Mockito.mock(CodexSessionCompactClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        String conversationState = Transcript.empty()
            .append("user", "a")
            .append("assistant", "b")
            .append("user", "c")
            .append("assistant", "d")
            .toConversationState(objectMapper);
        when(repository.findActive("3")).thenReturn(Optional.of(new ChatSessionRecord("3", conversationState, System.currentTimeMillis())));
        when(compactClient.compact(any())).thenReturn("sum");

        SessionService.SessionCompactResult result = new SessionService(repository, compactClient, objectMapper, Mockito.mock(ChatMemoryRepository.class)).compact("3");

        assertEquals(SessionService.SessionCompactResult.Status.OK, result.status());
        assertEquals(4, result.originalMessageCount());
        assertEquals("sum", result.compactText());
        verify(repository).persist(Mockito.eq("3"), any());
    }

    @Test
    void snapshotReturnsMessageStatsWhenSessionExists() {
        ChatSessionRepository repository = Mockito.mock(ChatSessionRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        String conversationState = Transcript.empty()
            .append("user", "a")
            .append("assistant", "b")
            .append("user", "c")
            .toConversationState(objectMapper);
        when(repository.findActive("3")).thenReturn(Optional.of(new ChatSessionRecord("3", conversationState, System.currentTimeMillis())));

        SessionService.SessionSnapshot snapshot = new SessionService(repository, Mockito.mock(CodexSessionCompactClient.class), objectMapper, Mockito.mock(ChatMemoryRepository.class)).snapshot("3");

        assertTrue(snapshot.active());
        assertEquals(3, snapshot.messageCount());
        assertEquals(2, snapshot.turnCount());
    }
}
