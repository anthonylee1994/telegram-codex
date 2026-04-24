package com.telegram.codex.integration.telegram.application.webhook;

import com.telegram.codex.integration.telegram.application.port.in.TelegramMessageParser;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TelegramWebhookHandler {

    private final TelegramMessageParser telegramUpdateParser;
    private final TelegramWebhookRouter webhookRouter;

    public TelegramWebhookHandler(
        TelegramMessageParser telegramUpdateParser,
        TelegramWebhookRouter webhookRouter
    ) {
        this.telegramUpdateParser = telegramUpdateParser;
        this.webhookRouter = webhookRouter;
    }

    public void handle(Map<String, Object> update) {
        InboundMessage message = telegramUpdateParser.parseIncomingTelegramMessage(update);
        webhookRouter.route(message, update);
    }
}
