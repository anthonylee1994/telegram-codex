package com.telegram.codex.interfaces.cli

import com.telegram.codex.integration.telegram.application.TelegramGateway
import com.telegram.codex.integration.telegram.domain.TelegramBotCommand
import com.telegram.codex.shared.AppProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.stereotype.Component

@Component
class CliTaskRunner(
    private val applicationContext: ConfigurableApplicationContext,
    private val properties: AppProperties,
    private val telegramClient: TelegramGateway,
    @param:Value($$"${app.task:}") private val task: String?,
) : CommandLineRunner {
    override fun run(vararg args: String) {
        if (task.isNullOrBlank()) {
            return
        }
        when (task) {
            "telegram:set-webhook" -> {
                telegramClient.setWebhook(properties.baseUrl + "/telegram/webhook", properties.telegramWebhookSecret)
            }
            "telegram:update-commands" -> {
                telegramClient.setMyCommands(
                    listOf(
                        TelegramBotCommand("status", "Bot 狀態"),
                        TelegramBotCommand("session", "目前 session 狀態"),
                        TelegramBotCommand("memory", "長期記憶狀態"),
                        TelegramBotCommand("forget", "清除長期記憶"),
                        TelegramBotCommand("compact", "壓縮目前對話 context"),
                        TelegramBotCommand("new", "新 session"),
                        TelegramBotCommand("help", "使用說明"),
                    ),
                )
            }
            else -> {
                throw IllegalArgumentException("Unknown task: $task")
            }
        }
        kotlin.system.exitProcess(org.springframework.boot.SpringApplication.exit(applicationContext, { 0 }))
    }
}
