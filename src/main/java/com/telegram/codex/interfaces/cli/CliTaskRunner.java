package com.telegram.codex.interfaces.cli;

import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import com.telegram.codex.integration.telegram.domain.TelegramBotCommand;
import com.telegram.codex.shared.config.AppProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CliTaskRunner implements CommandLineRunner {

    private final ConfigurableApplicationContext applicationContext;
    private final AppProperties properties;
    private final TelegramGateway telegramClient;
    private final String task;

    public CliTaskRunner(
        ConfigurableApplicationContext applicationContext,
        AppProperties properties,
        TelegramGateway telegramClient,
        @Value("${app.task:}") String task
    ) {
        this.applicationContext = applicationContext;
        this.properties = properties;
        this.telegramClient = telegramClient;
        this.task = task;
    }

    @Override
    public void run(String... args) {
        if (task == null || task.isBlank()) {
            return;
        }
        if ("telegram:set-webhook".equals(task)) {
            telegramClient.setWebhook(properties.getBaseUrl() + "/telegram/webhook", properties.getTelegramWebhookSecret());
        } else if ("telegram:update-commands".equals(task)) {
            telegramClient.setMyCommands(List.of(
                new TelegramBotCommand("status", "Bot 狀態"),
                new TelegramBotCommand("session", "目前 session 狀態"),
                new TelegramBotCommand("memory", "長期記憶狀態"),
                new TelegramBotCommand("forget", "清除長期記憶"),
                new TelegramBotCommand("compact", "壓縮目前對話 context"),
                new TelegramBotCommand("new", "新 session"),
                new TelegramBotCommand("help", "使用說明")
            ));
        } else {
            throw new IllegalArgumentException("Unknown task: " + task);
        }
        System.exit(org.springframework.boot.SpringApplication.exit(applicationContext, () -> 0));
    }
}
