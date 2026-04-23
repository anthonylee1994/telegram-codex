package com.telegram.codex.conversation.application.webhook;

import com.telegram.codex.conversation.application.update.ProcessedUpdateFlow;
import com.telegram.codex.conversation.application.webhook.command.CommandRegistry;
import com.telegram.codex.conversation.domain.ChatRateLimiter;
import com.telegram.codex.conversation.domain.Decision;
import com.telegram.codex.conversation.domain.update.ProcessedUpdateRecord;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import com.telegram.codex.shared.config.AppProperties;
import com.telegram.codex.conversation.domain.MessageConstants;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DecisionResolver {

    private final AppProperties properties;
    private final ChatRateLimiter rateLimiter;
    private final ProcessedUpdateFlow processedUpdateFlow;
    private final CommandRegistry commandRegistry;
    private final SensitiveIntentGuard sensitiveIntentGuard;

    public DecisionResolver(
        AppProperties properties,
        ChatRateLimiter rateLimiter,
        ProcessedUpdateFlow processedUpdateFlow,
        CommandRegistry commandRegistry,
        SensitiveIntentGuard sensitiveIntentGuard
    ) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.processedUpdateFlow = processedUpdateFlow;
        this.commandRegistry = commandRegistry;
        this.sensitiveIntentGuard = sensitiveIntentGuard;
    }

    public Decision call(InboundMessage message) {
        if (message == null || message.unsupported()) {
            return Decision.unsupported();
        }

        Optional<ProcessedUpdateRecord> processedUpdate = processedUpdateFlow.find(message.updateId());
        if (processedUpdateFlow.duplicate(processedUpdate)) {
            return Decision.duplicate(message);
        }
        if (processedUpdateFlow.replayable(processedUpdate)) {
            return Decision.replay(message, processedUpdate.orElseThrow());
        }

        if (!properties.allowedTelegramUserIds().isEmpty() && !properties.allowedTelegramUserIds().contains(message.userId())) {
            return Decision.rejectUnauthorized(message);
        }

        Optional<Decision> commandDecision = commandRegistry.executeCommand(message);
        if (commandDecision.isPresent()) {
            return commandDecision.get();
        }

        if (message.mediaGroup() && message.imageCount() > properties.getMaxMediaGroupImages()) {
            return Decision.tooManyImages(message, MessageConstants.TOO_MANY_IMAGES_MESSAGE);
        }

        if (sensitiveIntentGuard.shouldBlock(message)) {
            return Decision.rejectSensitiveIntent(message, MessageConstants.SENSITIVE_INTENT_MESSAGE);
        }

        if (!rateLimiter.allow(message.chatId())) {
            return Decision.rateLimited(message);
        }

        if (!processedUpdateFlow.beginProcessing(message)) {
            return Decision.duplicate(message);
        }

        return Decision.generateReply(message);
    }
}
