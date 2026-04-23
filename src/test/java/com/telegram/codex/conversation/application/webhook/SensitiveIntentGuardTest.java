package com.telegram.codex.conversation.application.webhook;

import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveIntentGuardTest {

    private final SensitiveIntentGuard guard = new SensitiveIntentGuard();

    @Test
    void blocksRequestsToInspectCodebase() {
        InboundMessage message = new InboundMessage("3", List.of(), null, 10, List.of(), List.of(), null, null, "你 code base 有咩 bugs?", "5", 99);

        assertTrue(guard.shouldBlock(message));
    }

    @Test
    void allowsNormalProductQuestions() {
        InboundMessage message = new InboundMessage("3", List.of(), null, 10, List.of(), List.of(), null, null, "幫我整理返重點", "5", 99);

        assertFalse(guard.shouldBlock(message));
    }
}
