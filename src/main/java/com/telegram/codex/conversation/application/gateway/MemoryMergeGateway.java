package com.telegram.codex.conversation.application.gateway;

public interface MemoryMergeGateway {

    String merge(String existingMemory, String userMessage, String assistantReply);
}
