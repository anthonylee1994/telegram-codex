package com.telegram.codex.conversation.application.port.out;

public interface MemoryMergePort {

    String merge(String existingMemory, String userMessage, String assistantReply);
}
