package com.telegram.codex.integration.telegram.domain.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(
    @JsonProperty("update_id") Long updateId,
    TelegramMessage message
) {
}
