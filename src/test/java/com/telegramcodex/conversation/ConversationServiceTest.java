package com.telegramcodex.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegramcodex.codex.CliClient;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ConversationServiceTest {

    @Test
    void sessionSnapshotReturnsInactiveWhenNoSession() {
        ConversationService service = new ConversationService(
            Mockito.mock(CliClient.class),
            Mockito.mock(MemoryClient.class),
            Mockito.mock(SessionSummaryClient.class),
            mockSessionRepository(Optional.empty()),
            Mockito.mock(ChatMemoryRepository.class),
            Mockito.mock(ProcessedUpdateRepository.class),
            new ObjectMapper()
        );

        Map<String, Object> snapshot = service.sessionSnapshot("3");

        assertFalse((Boolean) snapshot.get("active"));
    }

    @Test
    void generateReplyUsesStoredConversationState() {
        CliClient cliClient = Mockito.mock(CliClient.class);
        when(cliClient.generateReply(anyString(), any(), anyList(), any(), any()))
            .thenReturn(new ReplyResult("next-state", java.util.List.of("a", "b", "c"), "reply"));

        ChatSessionRepository sessionRepository = mockSessionRepository(Optional.of(new ChatSessionRecord("3", "[{\"role\":\"user\",\"content\":\"hi\"}]", System.currentTimeMillis())));
        ChatMemoryRepository memoryRepository = Mockito.mock(ChatMemoryRepository.class);
        when(memoryRepository.find("3")).thenReturn(Optional.of(new ChatMemoryRecord("3", "記憶", System.currentTimeMillis())));

        ConversationService service = new ConversationService(
            cliClient,
            Mockito.mock(MemoryClient.class),
            Mockito.mock(SessionSummaryClient.class),
            sessionRepository,
            memoryRepository,
            Mockito.mock(ProcessedUpdateRepository.class),
            new ObjectMapper()
        );

        ReplyResult reply = service.generateReply(new com.telegramcodex.telegram.InboundMessage(
            "3",
            java.util.List.of(),
            null,
            10,
            null,
            java.util.List.of(),
            java.util.List.of(),
            null,
            null,
            null,
            null,
            null,
            "你好",
            null,
            null,
            "5",
            99
        ), java.util.List.of(), null);

        assertEquals("reply", reply.text());
        assertEquals("next-state", reply.conversationState());
    }

    private ChatSessionRepository mockSessionRepository(Optional<ChatSessionRecord> record) {
        ChatSessionRepository repository = Mockito.mock(ChatSessionRepository.class);
        when(repository.findActive("3")).thenReturn(record);
        return repository;
    }
}
