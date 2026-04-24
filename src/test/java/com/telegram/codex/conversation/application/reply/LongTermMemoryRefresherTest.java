package com.telegram.codex.conversation.application.reply;

import com.telegram.codex.conversation.application.gateway.MemoryMergeGateway;
import com.telegram.codex.conversation.domain.memory.ChatMemoryRecord;
import com.telegram.codex.conversation.infrastructure.memory.ChatMemoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LongTermMemoryRefresherTest {

    @Test
    void refreshPersistsMergedMemoryWhenChanged() {
        ChatMemoryRepository memoryRepository = Mockito.mock(ChatMemoryRepository.class);
        MemoryMergeGateway memoryMergePort = Mockito.mock(MemoryMergeGateway.class);
        when(memoryRepository.find("3")).thenReturn(Optional.of(new ChatMemoryRecord("3", "舊記憶", System.currentTimeMillis())));
        when(memoryMergePort.merge("舊記憶", "新需求", "新回覆")).thenReturn("新記憶");

        LongTermMemoryRefresher refresher = new LongTermMemoryRefresher(memoryRepository, memoryMergePort);
        refresher.refresh("3", "新需求", "新回覆");

        verify(memoryRepository).persist("3", "新記憶");
    }

    @Test
    void refreshSkipsBlankUserMessage() {
        ChatMemoryRepository memoryRepository = Mockito.mock(ChatMemoryRepository.class);
        MemoryMergeGateway memoryMergePort = Mockito.mock(MemoryMergeGateway.class);

        LongTermMemoryRefresher refresher = new LongTermMemoryRefresher(memoryRepository, memoryMergePort);
        refresher.refresh("3", "   ", "新回覆");

        verify(memoryMergePort, never()).merge(Mockito.any(), Mockito.any(), Mockito.any());
        verify(memoryRepository, never()).persist(Mockito.any(), Mockito.any());
    }
}
