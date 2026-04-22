package com.telegram.codex.conversation.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.codex.Transcript;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SessionServiceTest {

    @Test
    void snapshotReturnsInactiveWhenNoSession() {
        ChatSessionRepository repository = Mockito.mock(ChatSessionRepository.class);
        when(repository.findActive("3")).thenReturn(Optional.empty());

        SessionSnapshot snapshot = new SessionService(repository, Mockito.mock(SessionCompactClient.class), new ObjectMapper()).snapshot("3");

        assertFalse(snapshot.active());
    }

    @Test
    void compactReturnsOkAndPersistsCompactTranscript() {
        ChatSessionRepository repository = Mockito.mock(ChatSessionRepository.class);
        SessionCompactClient compactClient = Mockito.mock(SessionCompactClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        String conversationState = Transcript.empty()
            .append("user", "a")
            .append("assistant", "b")
            .append("user", "c")
            .append("assistant", "d")
            .toConversationState(objectMapper);
        when(repository.findActive("3")).thenReturn(Optional.of(new ChatSessionRecord("3", conversationState, System.currentTimeMillis())));
        when(compactClient.compact(any())).thenReturn("sum");

        SessionCompactResult result = new SessionService(repository, compactClient, objectMapper).compact("3");

        assertEquals(SessionCompactResult.Status.OK, result.status());
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

        SessionSnapshot snapshot = new SessionService(repository, Mockito.mock(SessionCompactClient.class), objectMapper).snapshot("3");

        assertTrue(snapshot.active());
        assertEquals(3, snapshot.messageCount());
        assertEquals(2, snapshot.turnCount());
    }
}
