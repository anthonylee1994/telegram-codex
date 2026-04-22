package com.telegram.codex.conversation.webhooks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.telegram.codex.telegram.InboundMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

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
