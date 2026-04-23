package com.telegram.codex.conversation.domain.memory;

public record MemorySnapshot(boolean active, String memoryText, String lastUpdatedAt) {

    public static MemorySnapshot inactive() {
        return new MemorySnapshot(false, null, null);
    }

    public static MemorySnapshot active(String memoryText, String lastUpdatedAt) {
        return new MemorySnapshot(true, memoryText, lastUpdatedAt);
    }
}
