package com.telegram.codex.conversation.memory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MemoryServiceTest {

    @Test
    void snapshotReturnsInactiveWhenMemoryMissing() {
        ChatMemoryRepository repository = Mockito.mock(ChatMemoryRepository.class);
        when(repository.find("3")).thenReturn(Optional.empty());

        MemorySnapshot snapshot = new MemoryService(repository).snapshot("3");

        assertFalse(snapshot.active());
    }

    @Test
    void snapshotReturnsActiveWhenMemoryExists() {
        ChatMemoryRepository repository = Mockito.mock(ChatMemoryRepository.class);
        when(repository.find("3")).thenReturn(Optional.of(new ChatMemoryRecord("3", "記憶", 1_700_000_000_000L)));

        MemorySnapshot snapshot = new MemoryService(repository).snapshot("3");

        assertTrue(snapshot.active());
    }
}
