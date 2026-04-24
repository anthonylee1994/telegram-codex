package com.telegram.codex.integration.telegram.application.webhook;

import com.telegram.codex.conversation.application.ProcessedUpdateService;
import com.telegram.codex.conversation.domain.ChatRateLimiter;
import com.telegram.codex.conversation.domain.MessageConstants;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import com.telegram.codex.shared.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReplyRequestGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReplyRequestGuard.class);

    private final AppProperties properties;
    private final ChatRateLimiter rateLimiter;
    private final ProcessedUpdateService processedUpdateService;
    private final SensitiveIntentGuard sensitiveIntentGuard;
    private final TelegramGateway telegramClient;

    public ReplyRequestGuard(
        AppProperties properties,
        ChatRateLimiter rateLimiter,
        ProcessedUpdateService processedUpdateService,
        SensitiveIntentGuard sensitiveIntentGuard,
        TelegramGateway telegramClient
    ) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.processedUpdateService = processedUpdateService;
        this.sensitiveIntentGuard = sensitiveIntentGuard;
        this.telegramClient = telegramClient;
    }

    public boolean allow(InboundMessage message) {
        if (!properties.allowedTelegramUserIds().isEmpty() && !properties.allowedTelegramUserIds().contains(message.userId())) {
            LOGGER.warn("Rejected unauthorized Telegram user chat_id={} user_id={}", message.chatId(), message.userId());
            sendAndMarkProcessed(message, MessageConstants.UNAUTHORIZED_MESSAGE);
            return false;
        }

        if (message.mediaGroup() && message.imageCount() > properties.getMaxMediaGroupImages()) {
            sendAndMarkProcessed(message, MessageConstants.TOO_MANY_IMAGES_MESSAGE);
            return false;
        }

        if (sensitiveIntentGuard.shouldBlock(message)) {
            sendAndMarkProcessed(message, MessageConstants.SENSITIVE_INTENT_MESSAGE);
            return false;
        }

        if (!rateLimiter.allow(message.chatId())) {
            sendAndMarkProcessed(message, MessageConstants.RATE_LIMIT_MESSAGE);
            return false;
        }

        if (!processedUpdateService.beginProcessing(message)) {
            LOGGER.info("Ignored duplicate update update_id={}", message.updateId());
            return false;
        }
        return true;
    }

    private void sendAndMarkProcessed(InboundMessage message, String text) {
        telegramClient.sendMessage(message.chatId(), text, List.of(), false);
        processedUpdateService.markProcessed(message);
    }
}
