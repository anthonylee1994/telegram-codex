package com.telegram.codex.integration.telegram.application.webhook;

import com.telegram.codex.integration.telegram.application.port.in.TelegramMessageParser;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import com.telegram.codex.shared.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class TelegramWebhookHandler {

    private final AppProperties properties;
    private final InboundMessageProcessor inboundMessageProcessor;
    private final TelegramMessageParser telegramUpdateParser;

    public TelegramWebhookHandler(
        AppProperties properties,
        InboundMessageProcessor inboundMessageProcessor,
        TelegramMessageParser telegramUpdateParser
    ) {
        this.properties = properties;
        this.inboundMessageProcessor = inboundMessageProcessor;
        this.telegramUpdateParser = telegramUpdateParser;
    }

    public void handle(Map<String, Object> update) {
        InboundMessage message = telegramUpdateParser.parseIncomingTelegramMessage(update);
        if (message != null && message.mediaGroup()) {
            inboundMessageProcessor.deferMediaGroup(message, Duration.ofMillis(properties.getMediaGroupWaitMs()));
            return;
        }
        inboundMessageProcessor.process(message, update);
    }
}
