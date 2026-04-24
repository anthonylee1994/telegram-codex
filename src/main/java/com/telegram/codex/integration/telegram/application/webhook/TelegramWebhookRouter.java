package com.telegram.codex.integration.telegram.application.webhook;

import com.telegram.codex.integration.telegram.domain.InboundMessage;
import com.telegram.codex.shared.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class TelegramWebhookRouter {

    private final AppProperties properties;
    private final InboundMessageProcessor inboundMessageProcessor;

    public TelegramWebhookRouter(AppProperties properties, InboundMessageProcessor inboundMessageProcessor) {
        this.properties = properties;
        this.inboundMessageProcessor = inboundMessageProcessor;
    }

    public void route(InboundMessage message, Map<String, Object> update) {
        if (shouldDeferMediaGroup(message)) {
            inboundMessageProcessor.deferMediaGroup(message, Duration.ofMillis(properties.getMediaGroupWaitMs()));
            return;
        }
        inboundMessageProcessor.process(message, update);
    }

    private boolean shouldDeferMediaGroup(InboundMessage message) {
        return message != null && message.mediaGroup();
    }
}
