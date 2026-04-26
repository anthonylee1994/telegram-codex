package com.telegram.codex.conversation.domain

import com.telegram.codex.integration.telegram.domain.InboundMessage
import com.telegram.codex.shared.config.AppProperties
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object ConversationConstants {
    const val MAX_TRANSCRIPT_MESSAGES: Int = 100
    const val MIN_TRANSCRIPT_SIZE_FOR_COMPACT: Int = 4
    const val PROCESSED_UPDATE_PRUNE_INTERVAL_MS: Long = 6L * 60 * 60 * 1000
    const val PROCESSED_UPDATE_RETENTION_MS: Long = 30L * 24 * 60 * 60 * 1000
}

@Component
class ChatRateLimiter(
    private val properties: AppProperties,
) {
    private val hits = ConcurrentHashMap<String, CopyOnWriteArrayList<Long>>()

    fun allow(chatId: String): Boolean {
        val now = System.currentTimeMillis()
        val chatHits = hits.computeIfAbsent(chatId) { CopyOnWriteArrayList() }
        chatHits.removeIf { timestamp -> now - timestamp >= properties.rateLimitWindowMs }
        if (chatHits.size >= properties.rateLimitMaxMessages) {
            return false
        }
        chatHits.add(now)
        return true
    }
}

object MessageConstants {
    val HELP_MESSAGE: String = listOf(
        "可用 command：",
        "/help - 顯示可用指令",
        "/status - 睇 bot 狀態",
        "/session - 睇目前 session 狀態",
        "/memory - 睇長期記憶",
        "/forget - 清除長期記憶",
        "/compact - 將長對話壓縮成新 context",
        "/new - 開新 session",
        "",
        "你亦可以直接講「記住...」、「將長期記憶改成...」、「忘記...」嚟管理長期記憶。",
        "",
        "我而家僅支持文字、圖片。",
    ).joinToString("\n")

    const val NEW_SESSION_MESSAGE: String = "已經開咗個新 session，你可以重新開始。"
    const val RATE_LIMIT_MESSAGE: String = "你打得太快，等一陣再試。"
    const val RESET_MEMORY_MESSAGE: String = "已經刪除長期記憶。"
    const val COMPACT_QUEUED_MESSAGE: String = "開始 compact 目前 session。整完之後我會再主動 send 結果畀你。"
    const val COMPACT_BASELINE_MESSAGE: String = "以下係之前對話 compact 後嘅內容。之後請按呢份內容延續對話上下文。"

    val START_MESSAGE: String = listOf(
        "您好，我係您嘅 AI 助手。",
        "",
        "直接 send 文字或者圖片畀我就得。",
        "想睇指令就打 /help。",
        "想重新開過個 session，就打 /new。",
    ).joinToString("\n")

    const val TOO_MANY_IMAGES_MESSAGE: String = "你一次過畀太多圖，我未必可以準確逐張睇。揀最多 10 張最關鍵嘅圖，或者直接講明想我集中比較邊幾張、邊一方面。"
    const val SENSITIVE_INTENT_MESSAGE: String = "我唔會主動檢查本機 codebase、repo、system prompt 或內部檔案。如果你想我 review 某段 code，直接貼內容出嚟。"
    const val UNAUTHORIZED_MESSAGE: String = "呢個 bot 暫時只限指定用戶使用。"
    const val UNSUPPORTED_MESSAGE: String = "你輸入嘅內容，我仲未識得處理。"
    const val REPLY_TO_IMAGE: String = "用戶引用咗一張相。"
    const val REPLY_TO_IMAGE_DOCUMENT: String = "用戶引用咗一個圖片檔案。"

    val DEFAULT_SUGGESTED_REPLIES: List<String> = listOf(
        "可唔可以講詳細啲？",
        "幫我列重點。",
        "下一步可以點做？",
    )
}

@Component
class MediaGroupMerger {
    fun merge(messages: List<InboundMessage>): InboundMessage {
        require(messages.isNotEmpty()) { "Cannot merge empty message list" }
        val sorted = messages.sortedWith(compareBy<InboundMessage> { it.messageId }.thenBy { it.updateId })
        val primary = sorted.first()
        val aggregatedText = sorted.mapNotNull { it.text }.firstOrNull { it.isNotBlank() }
        val aggregatedImageFileIds = sorted.flatMap { it.imageFileIds }.distinct()
        val processingUpdates = sorted.map { InboundMessage.ProcessingUpdate(it.updateId, it.messageId) }
        return InboundMessage.forMergedMediaGroup(primary, aggregatedImageFileIds, processingUpdates, aggregatedText)
    }
}

object ConversationTimeFormatter {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
        .withZone(ZoneId.of("Asia/Hong_Kong"))

    fun format(epochMillis: Long): String = formatter.format(Instant.ofEpochMilli(epochMillis))
}
