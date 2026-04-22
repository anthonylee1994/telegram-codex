package com.telegram.codex.telegram;

import com.telegram.codex.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class TelegramWebhookHandler {

    public static final Object DEFERRED = new Object();

    private final AppProperties properties;
    private final InboundMessageProcessor inboundMessageProcessor;
    private final TelegramUpdateParser telegramUpdateParser;

    public TelegramWebhookHandler(
        AppProperties properties,
        InboundMessageProcessor inboundMessageProcessor,
        TelegramUpdateParser telegramUpdateParser
    ) {
        this.properties = properties;
        this.inboundMessageProcessor = inboundMessageProcessor;
        this.telegramUpdateParser = telegramUpdateParser;
    }

    public void handle(Map<String, Object> update) {
        InboundMessage message = telegramUpdateParser.parseIncomingTelegramMessage(update);
        if (message != null && message.mediaGroup()) {
            Object deferred = inboundMessageProcessor.deferMediaGroup(message, Duration.ofMillis(properties.getMediaGroupWaitMs()));
            if (deferred == DEFERRED) {
                return;
            }
        }
        inboundMessageProcessor.process(message, update);
    }
}
