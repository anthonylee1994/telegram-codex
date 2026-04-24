package com.telegram.codex.integration.telegram.application.webhook;

import com.telegram.codex.integration.telegram.application.port.in.TelegramMessageParser;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import com.telegram.codex.integration.telegram.domain.webhook.TelegramUpdate;
import org.springframework.stereotype.Component;

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

    public void handle(TelegramUpdate update) {
        // Handler 只做 parse + handoff；真正 routing 留返俾 router，避免呢層再塞入業務判斷。
        InboundMessage message = telegramUpdateParser.parseIncomingTelegramMessage(update);
        webhookRouter.route(message, update);
    }
}
