package com.telegram.codex.conversation.application.reply;

import com.telegram.codex.conversation.application.port.out.ChatMemoryPort;
import com.telegram.codex.conversation.application.port.out.ChatSessionPort;
import com.telegram.codex.conversation.application.port.out.MemoryMergePort;
import com.telegram.codex.conversation.application.port.out.ProcessedUpdatePort;
import com.telegram.codex.conversation.application.port.out.ReplyGenerationPort;
import com.telegram.codex.conversation.domain.memory.ChatMemoryRecord;
import com.telegram.codex.conversation.domain.session.ChatSessionRecord;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class ConversationServiceTest {

    @Test
    void generateReplyUsesStoredConversationState() {
        ReplyGenerationPort cliClient = Mockito.mock(ReplyGenerationPort.class);
        when(cliClient.generateReply(anyString(), any(), anyList(), any(), any())).thenReturn(new ReplyResult("next-state", List.of("a", "b", "c"), "reply"));

        ChatSessionPort sessionRepository = mockSessionRepository(Optional.of(new ChatSessionRecord("3", "[{\"role\":\"user\",\"content\":\"hi\"}]", System.currentTimeMillis())));
        ChatMemoryPort memoryRepository = Mockito.mock(ChatMemoryPort.class);
        when(memoryRepository.find("3")).thenReturn(Optional.of(new ChatMemoryRecord("3", "記憶", System.currentTimeMillis())));

        ConversationService service = new ConversationService(
            cliClient,
            Mockito.mock(MemoryMergePort.class),
            sessionRepository,
            memoryRepository,
            Mockito.mock(ProcessedUpdatePort.class)
        );

        ReplyResult reply = service.generateReply(buildMessage(), List.of(), null);

        assertEquals("reply", reply.text());
        assertEquals("next-state", reply.conversationState());
    }

    private ChatSessionPort mockSessionRepository(Optional<ChatSessionRecord> record) {
        ChatSessionPort repository = Mockito.mock(ChatSessionPort.class);
        when(repository.findActive("3")).thenReturn(record);
        return repository;
    }

    private InboundMessage buildMessage() {
        return new InboundMessage("3", List.of(), null, 10, List.of(), List.of(), null, null, "你好", "5", 99);
    }
}
