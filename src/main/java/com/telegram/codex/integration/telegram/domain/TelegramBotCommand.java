package com.telegram.codex.integration.telegram.domain;

public record TelegramBotCommand(
    String command,
    String description
) {
}
