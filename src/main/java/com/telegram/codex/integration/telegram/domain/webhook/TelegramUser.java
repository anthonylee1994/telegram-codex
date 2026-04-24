package com.telegram.codex.integration.telegram.domain.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUser(
    Long id
) {
}
