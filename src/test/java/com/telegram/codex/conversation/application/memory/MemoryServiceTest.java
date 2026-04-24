package com.telegram.codex.conversation.application.memory;

import com.telegram.codex.conversation.domain.memory.ChatMemoryRecord;
import com.telegram.codex.conversation.infrastructure.memory.ChatMemoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class MemoryServiceTest {

    @Test
    void snapshotReturnsInactiveWhenMemoryMissing() {
        ChatMemoryRepository repository = Mockito.mock(ChatMemoryRepository.class);
        when(repository.find("3")).thenReturn(Optional.empty());

        MemoryService.MemorySnapshot snapshot = new MemoryService(repository).snapshot("3");

        assertFalse(snapshot.active());
    }

    @Test
    void snapshotReturnsActiveWhenMemoryExists() {
        ChatMemoryRepository repository = Mockito.mock(ChatMemoryRepository.class);
        when(repository.find("3")).thenReturn(Optional.of(new ChatMemoryRecord("3", "記憶", 1_700_000_000_000L)));

        MemoryService.MemorySnapshot snapshot = new MemoryService(repository).snapshot("3");

        assertTrue(snapshot.active());
    }
}
