package com.telegram.codex.integration.telegram.application

import com.telegram.codex.conversation.application.SessionService
import org.springframework.stereotype.Component

@Component
class CompactResultSender(
    private val telegramClient: TelegramGateway,
) {
    fun send(chatId: String, result: SessionService.SessionCompactResult) {
        val text = when (result.status) {
            SessionService.SessionCompactResult.Status.MISSING_SESSION -> "而家冇 active session，冇嘢可以 compact。"
            SessionService.SessionCompactResult.Status.TOO_SHORT -> "目前對話得 ${result.messageCount} 段訊息，未去到要壓縮 context。"
            SessionService.SessionCompactResult.Status.OK -> listOf(
                "已經將目前 session compact 成新 context。",
                "原本訊息：${result.originalMessageCount}",
                "",
                result.compactText,
            ).joinToString("\n")
        }
        telegramClient.sendMessage(chatId, text, emptyList(), true)
    }
}
