package com.telegram.codex.web;

import com.telegram.codex.config.AppProperties;
import com.telegram.codex.telegram.TelegramWebhookHandler;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TelegramWebhookController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramWebhookController.class);

    private final AppProperties properties;
    private final TelegramWebhookHandler webhookHandler;

    public TelegramWebhookController(AppProperties properties, TelegramWebhookHandler webhookHandler) {
        this.properties = properties;
        this.webhookHandler = webhookHandler;
    }

    @PostMapping("/telegram/webhook")
    public ResponseEntity<Map<String, Object>> create(
        @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
        @RequestBody(required = false) Map<String, Object> payload
    ) {
        if (!properties.getTelegramWebhookSecret().equals(secretToken)) {
            LOGGER.warn("Rejected Telegram webhook with invalid secret");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false));
        }
        try {
            webhookHandler.handle(payload == null ? Map.of() : payload);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception error) {
            LOGGER.error("Failed to process Telegram webhook: {}", error.getMessage(), error);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("ok", false));
        }
    }
}
