package com.telegram.codex.conversation.application.reply;

import com.telegram.codex.conversation.domain.memory.ChatMemoryRecord;
import com.telegram.codex.conversation.domain.session.ChatSessionRecord;
import com.telegram.codex.conversation.infrastructure.memory.ChatMemoryRepository;
import com.telegram.codex.conversation.infrastructure.session.ChatSessionRepository;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

class ReplyContextLoaderTest {

    @Test
    void loadIncludesSessionMemoryAndReplyContext() {
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        ChatMemoryRepository memoryRepository = Mockito.mock(ChatMemoryRepository.class);
        when(sessionRepository.findActive("3")).thenReturn(Optional.of(new ChatSessionRecord("3", "state-123", System.currentTimeMillis())));
        when(memoryRepository.find("3")).thenReturn(Optional.of(new ChatMemoryRecord("3", "記住我鍾意 Java", System.currentTimeMillis())));

        ReplyContextLoader loader = new ReplyContextLoader(sessionRepository, memoryRepository);
        ReplyContextSnapshot snapshot = loader.load(new InboundMessage(
            "3",
            List.of(),
            null,
            10,
            List.of(),
            List.of(),
            null,
            "上條訊息",
            "你好",
            "5",
            99
        ));

        assertEquals("你好", snapshot.promptText());
        assertEquals("state-123", snapshot.lastResponseId());
        assertEquals("上條訊息", snapshot.replyToText());
        assertEquals("記住我鍾意 Java", snapshot.memoryText());
    }

    @Test
    void loadFallsBackWhenSessionAndMemoryAreMissing() {
        ChatSessionRepository sessionRepository = Mockito.mock(ChatSessionRepository.class);
        ChatMemoryRepository memoryRepository = Mockito.mock(ChatMemoryRepository.class);
        when(sessionRepository.findActive("3")).thenReturn(Optional.empty());
        when(memoryRepository.find("3")).thenReturn(Optional.empty());

        ReplyContextLoader loader = new ReplyContextLoader(sessionRepository, memoryRepository);
        ReplyContextSnapshot snapshot = loader.load(new InboundMessage(
            "3",
            List.of(),
            null,
            10,
            List.of(),
            List.of(),
            null,
            null,
            null,
            "5",
            99
        ));

        assertEquals("", snapshot.promptText());
        assertNull(snapshot.lastResponseId());
        assertNull(snapshot.replyToText());
        assertNull(snapshot.memoryText());
    }
}
