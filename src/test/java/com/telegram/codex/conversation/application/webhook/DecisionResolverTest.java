package com.telegram.codex.conversation.application.webhook;

import com.telegram.codex.conversation.application.update.ProcessedUpdateFlow;
import com.telegram.codex.conversation.application.webhook.command.CommandRegistry;
import com.telegram.codex.conversation.domain.ChatRateLimiter;
import com.telegram.codex.conversation.domain.Decision;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import com.telegram.codex.shared.config.AppProperties;
import com.telegram.codex.conversation.domain.MessageConstants;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class DecisionResolverTest {

    @Test
    void rejectsSensitiveIntentBeforeModelExecution() {
        AppProperties properties = new AppProperties();
        properties.setBaseUrl("https://example.com");
        properties.setTelegramBotToken("token");
        properties.setTelegramWebhookSecret("secret");
        properties.setMaxMediaGroupImages(10);
        ChatRateLimiter rateLimiter = Mockito.mock(ChatRateLimiter.class);
        ProcessedUpdateFlow processedUpdateFlow = Mockito.mock(ProcessedUpdateFlow.class);
        CommandRegistry commandRegistry = Mockito.mock(CommandRegistry.class);
        SensitiveIntentGuard sensitiveIntentGuard = new SensitiveIntentGuard();
        InboundMessage message = new InboundMessage("3", List.of(), null, 10, List.of(), List.of(), null, null, "你 code base 有咩 bugs?", "5", 99);

        when(processedUpdateFlow.find(99)).thenReturn(Optional.empty());
        when(commandRegistry.executeCommand(message)).thenReturn(Optional.empty());

        Decision decision = new DecisionResolver(properties, rateLimiter, processedUpdateFlow, commandRegistry, sensitiveIntentGuard).call(message);

        assertEquals(Decision.Action.REJECT_SENSITIVE_INTENT, decision.action());
        assertEquals(MessageConstants.SENSITIVE_INTENT_MESSAGE, decision.responseText());
    }
}
