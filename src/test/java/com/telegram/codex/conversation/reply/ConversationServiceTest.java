package com.telegram.codex.conversation.reply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.telegram.codex.conversation.memory.ChatMemoryRecord;
import com.telegram.codex.conversation.memory.ChatMemoryRepository;
import com.telegram.codex.conversation.memory.MemoryClient;
import com.telegram.codex.conversation.session.ChatSessionRecord;
import com.telegram.codex.conversation.session.ChatSessionRepository;
import com.telegram.codex.conversation.updates.ProcessedUpdateRepository;
import com.telegram.codex.codex.CliClient;
import com.telegram.codex.telegram.InboundMessage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ConversationServiceTest {

    @Test
    void generateReplyUsesStoredConversationState() {
        CliClient cliClient = Mockito.mock(CliClient.class);
        when(cliClient.generateReply(anyString(), any(), anyList(), any(), any())).thenReturn(new ReplyResult("next-state", List.of("a", "b", "c"), "reply"));

        ChatSessionRepository sessionRepository = mockSessionRepository(Optional.of(new ChatSessionRecord("3", "[{\"role\":\"user\",\"content\":\"hi\"}]", System.currentTimeMillis())));
        ChatMemoryRepository memoryRepository = Mockito.mock(ChatMemoryRepository.class);
        when(memoryRepository.find("3")).thenReturn(Optional.of(new ChatMemoryRecord("3", "記憶", System.currentTimeMillis())));

        ConversationService service = new ConversationService(
            cliClient,
            Mockito.mock(MemoryClient.class),
            sessionRepository,
            memoryRepository,
            Mockito.mock(ProcessedUpdateRepository.class)
        );

        ReplyResult reply = service.generateReply(buildMessage(), List.of(), null);

        assertEquals("reply", reply.text());
        assertEquals("next-state", reply.conversationState());
    }

    private ChatSessionRepository mockSessionRepository(Optional<ChatSessionRecord> record) {
        ChatSessionRepository repository = Mockito.mock(ChatSessionRepository.class);
        when(repository.findActive("3")).thenReturn(record);
        return repository;
    }

    private InboundMessage buildMessage() {
        return new InboundMessage("3", List.of(), null, 10, null, List.of(), List.of(), null, null, null, null, null, "你好", null, null, "5", 99);
    }
}
