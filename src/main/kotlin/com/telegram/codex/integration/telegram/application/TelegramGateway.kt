package com.telegram.codex.integration.telegram.application

import com.telegram.codex.integration.telegram.domain.TelegramBotCommand
import java.nio.file.Path

interface TelegramGateway {
    fun downloadFileToTemp(fileId: String): Path
    fun sendMessage(chatId: String, text: String?, suggestedReplies: List<String>, removeKeyboard: Boolean)
    fun <T> withTypingStatus(chatId: String, action: () -> T): T
    fun setWebhook(url: String, secretToken: String)
    fun setMyCommands(commands: List<TelegramBotCommand>)
}
