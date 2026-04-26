package com.telegram.codex.integration.telegram.infrastructure

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.telegram.codex.integration.codex.ReplyParser
import com.telegram.codex.integration.telegram.application.port.`in`.TelegramMessageParser
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway
import com.telegram.codex.integration.telegram.domain.InboundMessage
import com.telegram.codex.integration.telegram.domain.MessageExtractor
import com.telegram.codex.integration.telegram.domain.TelegramBotCommand
import com.telegram.codex.integration.telegram.domain.TelegramConstants
import com.telegram.codex.integration.telegram.domain.webhook.TelegramUpdate
import com.telegram.codex.shared.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import java.util.regex.Matcher
import java.util.regex.Pattern

@Component
class TelegramApiClient(
    private val properties: AppProperties,
    private val objectMapper: ObjectMapper,
    private val httpClient: HttpClient,
    private val typingStatusManager: TypingStatusManager,
) {
    fun postForm(methodName: String, params: TelegramFormRequest) {
        try {
            val body = buildFormBody(params)
            val request = HttpRequest.newBuilder(URI.create("${apiBase()}/$methodName"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            validateStatusCode(response.statusCode(), "call Telegram $methodName")
            val payload = objectMapper.readValue(response.body(), object : TypeReference<TelegramApiResponse<JsonNode>>() {})
            validateTelegramResponse(payload, methodName)
        } catch (error: IOException) {
            throw IllegalStateException("Failed to call Telegram $methodName", error)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Failed to call Telegram $methodName", error)
        }
    }

    fun <T> withTypingStatus(chatId: String, action: Supplier<T>): T =
        typingStatusManager.withTypingStatus(chatId, Consumer { id -> sendChatAction(id, "typing") }, action)

    fun sendChatAction(chatId: String, action: String) {
        postForm("sendChatAction", SendChatActionRequest(chatId, action))
    }

    private fun buildFormBody(params: TelegramFormRequest): String =
        params.toFormFields().joinToString("&") { entry ->
            "${URLEncoder.encode(entry.key, StandardCharsets.UTF_8)}=${URLEncoder.encode(entry.value, StandardCharsets.UTF_8)}"
        }

    private fun apiBase(): String = TelegramConstants.TELEGRAM_API_BASE + properties.telegramBotToken

    private fun validateStatusCode(statusCode: Int, operation: String) {
        if (statusCode / 100 != 2) {
            throw IllegalStateException("Failed to $operation: HTTP $statusCode")
        }
    }

    private fun validateTelegramResponse(payload: TelegramApiResponse<*>?, operation: String) {
        if (payload == null || payload.ok != true) {
            throw IllegalStateException("Failed to $operation: invalid response")
        }
    }

    interface TelegramFormRequest {
        fun toFormFields(): List<FormField>
    }

    data class FormField(val key: String, val value: String)

    data class SendMessageRequest(
        val chatId: String,
        val text: String?,
        val parseMode: String,
        val replyMarkup: String?,
    ) : TelegramFormRequest {
        override fun toFormFields(): List<FormField> {
            val fields = ArrayList<FormField>()
            fields.add(FormField("chat_id", chatId))
            fields.add(FormField("text", text ?: ""))
            fields.add(FormField("parse_mode", parseMode))
            if (!replyMarkup.isNullOrBlank()) {
                fields.add(FormField("reply_markup", replyMarkup))
            }
            return fields.toList()
        }
    }

    data class SendChatActionRequest(val chatId: String, val action: String) : TelegramFormRequest {
        override fun toFormFields(): List<FormField> = listOf(FormField("chat_id", chatId), FormField("action", action))
    }

    data class SetWebhookRequest(val url: String, val secretToken: String) : TelegramFormRequest {
        override fun toFormFields(): List<FormField> = listOf(FormField("url", url), FormField("secret_token", secretToken))
    }

    data class SetMyCommandsRequest(val commands: String) : TelegramFormRequest {
        override fun toFormFields(): List<FormField> = listOf(FormField("commands", commands))
    }

    private data class TelegramApiResponse<T>(
        @param:JsonProperty("ok") val ok: Boolean?,
        @param:JsonProperty("result") val result: T?,
    )
}

@Component
class TypingStatusManager {
    private val scheduler = Executors.newScheduledThreadPool(1) { runnable ->
        Thread(runnable).apply { isDaemon = true }
    }

    fun <T> withTypingStatus(chatId: String, sendTypingAction: Consumer<String>, action: Supplier<T>): T {
        try {
            sendTypingAction.accept(chatId)
        } catch (error: Exception) {
            LOGGER.debug("Failed to send initial typing status for chat_id={}", chatId, error)
        }

        val typingTask = scheduler.scheduleAtFixedRate({
            try {
                sendTypingAction.accept(chatId)
            } catch (error: Exception) {
                LOGGER.debug("Failed to send periodic typing status for chat_id={}", chatId, error)
            }
        }, 4, 4, TimeUnit.SECONDS)

        try {
            return action.get()
        } finally {
            typingTask.cancel(true)
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(TypingStatusManager::class.java)
    }
}

@Component
class TelegramFileDownloader(
    private val properties: AppProperties,
    private val objectMapper: ObjectMapper,
    private val httpClient: HttpClient,
) {
    fun downloadFileToTemp(fileId: String): Path {
        val file = getFile(fileId)
        val filePath = file.filePath
        try {
            val tempDir = Files.createTempDirectory("telegram-codex-file-")
            val outputPath = tempDir.resolve(Path.of(filePath).fileName.toString())
            val request = HttpRequest.newBuilder(
                URI.create(TelegramConstants.TELEGRAM_FILE_API_BASE + properties.telegramBotToken + "/" + filePath),
            ).GET().build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
            validateStatusCode(response.statusCode(), "download Telegram file")
            Files.write(outputPath, response.body())
            return outputPath
        } catch (error: IOException) {
            throw IllegalStateException("Failed to download Telegram file", error)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Failed to download Telegram file", error)
        }
    }

    private fun getFile(fileId: String): TelegramFileResult {
        try {
            val request = HttpRequest.newBuilder(
                URI.create(
                    TelegramConstants.TELEGRAM_API_BASE + properties.telegramBotToken +
                        "/getFile?file_id=" + URLEncoder.encode(fileId, StandardCharsets.UTF_8),
                ),
            ).GET().build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            validateStatusCode(response.statusCode(), "call Telegram getFile")
            val payload = objectMapper.readValue(response.body(), object : TypeReference<TelegramApiResponse<TelegramFileResult>>() {})
            validateTelegramResponse(payload)
            val result = payload.result
            if (result?.filePath.isNullOrBlank()) {
                throw IllegalStateException("Telegram getFile did not include a file path")
            }
            return result
        } catch (error: IOException) {
            throw IllegalStateException("Failed to call Telegram getFile", error)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Failed to call Telegram getFile", error)
        }
    }

    private fun validateStatusCode(statusCode: Int, operation: String) {
        if (statusCode / 100 != 2) {
            throw IllegalStateException("Failed to $operation: HTTP $statusCode")
        }
    }

    private fun validateTelegramResponse(payload: TelegramApiResponse<*>?) {
        if (payload == null || payload.ok != true) {
            throw IllegalStateException("Failed to call Telegram getFile: invalid response")
        }
    }

    private data class TelegramApiResponse<T>(
        @param:JsonProperty("ok") val ok: Boolean?,
        @param:JsonProperty("result") val result: T?,
    )

    private data class TelegramFileResult(
        @param:JsonProperty("file_path") val filePath: String?,
    )
}

@Component
class TelegramClient(
    private val apiClient: TelegramApiClient,
    private val fileDownloader: TelegramFileDownloader,
    private val messageFormatter: TelegramMessageFormatter,
    private val objectMapper: ObjectMapper,
) : TelegramGateway {
    override fun downloadFileToTemp(fileId: String): Path = fileDownloader.downloadFileToTemp(fileId)

    override fun sendMessage(chatId: String, text: String?, suggestedReplies: List<String>, removeKeyboard: Boolean) {
        val normalizedReply = messageFormatter.normalizeReply(text, suggestedReplies)
        val replyMarkup = messageFormatter.buildReplyMarkup(normalizedReply.suggestedReplies, removeKeyboard)
        apiClient.postForm(
            "sendMessage",
            TelegramApiClient.SendMessageRequest(
                chatId,
                messageFormatter.formatForTelegram(normalizedReply.text),
                "HTML",
                replyMarkup?.let(::writeJson),
            ),
        )
    }

    override fun <T> withTypingStatus(chatId: String, action: Supplier<T>): T = apiClient.withTypingStatus(chatId, action)

    override fun setWebhook(url: String, secretToken: String) {
        apiClient.postForm("setWebhook", TelegramApiClient.SetWebhookRequest(url, secretToken))
        LOGGER.info("Telegram webhook configured url={}", url)
    }

    override fun setMyCommands(commands: List<TelegramBotCommand>) {
        apiClient.postForm("setMyCommands", TelegramApiClient.SetMyCommandsRequest(writeJson(commands)))
        LOGGER.info("Telegram commands updated count={}", commands.size)
    }

    private fun writeJson(payload: Any): String =
        try {
            objectMapper.writeValueAsString(payload)
        } catch (error: IOException) {
            throw IllegalStateException("Failed to serialize JSON", error)
        }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(TelegramClient::class.java)
    }
}

@Component
class TelegramUpdateParser : TelegramMessageParser {
    override fun parseIncomingTelegramMessage(update: TelegramUpdate?): InboundMessage? {
        val message = extractMessage(update)
        if (!isValidUpdate(update, message)) {
            return null
        }
        val extractor = MessageExtractor.from(message!!)
        if (!extractor.isSupported()) {
            return null
        }
        return buildInboundMessage(update!!, extractor)
    }

    private fun buildInboundMessage(update: TelegramUpdate, extractor: MessageExtractor): InboundMessage {
        val replyToMessage = extractor.getReplyToMessage().orElse(null)
        return InboundMessage(
            extractor.getChatId(),
            extractor.getImageFileIds(),
            extractor.getMediaGroupId(),
            extractor.getMessageId(),
            emptyList(),
            replyToMessage?.getImageFileIds() ?: emptyList(),
            replyToMessage?.getMessageId(),
            extractor.getReplyToText().orElse(null),
            extractor.getText(),
            extractor.getUserId(),
            update.updateId!!,
        )
    }

    private fun extractMessage(update: TelegramUpdate?) = update?.message

    private fun isValidUpdate(update: TelegramUpdate?, message: com.telegram.codex.integration.telegram.domain.webhook.TelegramMessage?): Boolean {
        if (update?.updateId == null) {
            return false
        }
        if (message?.messageId == null) {
            return false
        }
        return message.from != null && message.chat != null
    }
}

@Component
class TelegramMessageFormatter(
    private val replyParser: ReplyParser?,
) {
    fun formatForTelegram(text: String?): String {
        if (text.isNullOrEmpty()) {
            return ""
        }
        val formatted = StringBuilder()
        val matcher = FENCED_CODE_BLOCK_PATTERN.matcher(text)
        var cursor = 0
        while (matcher.find()) {
            formatted.append(formatInlineSegment(text.substring(cursor, matcher.start())))
            formatted.append(renderCodeBlock(matcher.group(1)))
            cursor = matcher.end()
        }
        formatted.append(formatInlineSegment(text.substring(cursor)))
        return formatted.toString()
    }

    fun buildReplyMarkup(suggestedReplies: List<String>?, removeKeyboard: Boolean): TelegramReplyMarkup? {
        if (removeKeyboard) {
            return RemoveKeyboardMarkup(true)
        }
        val cleanedReplies = cleanReplies(suggestedReplies.orEmpty())
        if (cleanedReplies.isEmpty()) {
            return null
        }
        val keyboard = cleanedReplies.map { reply -> listOf(KeyboardButton(reply)) }
        return ReplyKeyboardMarkup(keyboard, true, true)
    }

    private fun cleanReplies(replies: List<String>): List<String> {
        val cleaned = ArrayList<String>()
        for (reply in replies) {
            if (reply.isBlank()) {
                continue
            }
            val normalized = reply.trim()
            if (!cleaned.contains(normalized)) {
                cleaned.add(normalized)
            }
            if (cleaned.size == TelegramConstants.MAX_SUGGESTED_REPLIES) {
                break
            }
        }
        return cleaned.toList()
    }

    fun normalizeReply(text: String?, suggestedReplies: List<String>?): NormalizedReply {
        val payload = replyParser?.parseReply(text) ?: ReplyParser.ParsedReply(text.orEmpty(), emptyList())
        val normalizedText = if (payload.text.isBlank()) text.orEmpty() else payload.text
        val normalizedSuggestedReplies = if (suggestedReplies.isNullOrEmpty()) payload.suggestedReplies else suggestedReplies
        return NormalizedReply(normalizedText, normalizedSuggestedReplies)
    }

    data class NormalizedReply(val text: String, val suggestedReplies: List<String>)

    sealed interface TelegramReplyMarkup

    data class RemoveKeyboardMarkup(
        @param:JsonProperty("remove_keyboard") val removeKeyboard: Boolean,
    ) : TelegramReplyMarkup

    data class ReplyKeyboardMarkup(
        @param:JsonProperty("keyboard") val keyboard: List<List<KeyboardButton>>,
        @param:JsonProperty("resize_keyboard") val resizeKeyboard: Boolean,
        @param:JsonProperty("one_time_keyboard") val oneTimeKeyboard: Boolean,
    ) : TelegramReplyMarkup

    data class KeyboardButton(
        @param:JsonProperty("text") val text: String,
    )

    private data class InlineRule(
        val pattern: Pattern,
        val replacement: Function<Matcher, String>,
    )

    companion object {
        private val FENCED_CODE_BLOCK_PATTERN = Pattern.compile("```(?:[\\t ]*[\\w#+.-]+)?\\n?(.*?)```", Pattern.DOTALL)
        private val INLINE_CODE_PATTERN = Pattern.compile("`([^`\\n]+)`")
        private val SPOILER_PATTERN = Pattern.compile("\\|\\|([^|\\n]+)\\|\\|")
        private val STRIKETHROUGH_PATTERN = Pattern.compile("~~([^~\\n]+)~~")
        private val BOLD_PATTERN = Pattern.compile("\\*\\*([^*\\n]+)\\*\\*")
        private val ITALIC_PATTERN = Pattern.compile("(?<!_)__([^_\\n]+)__(?!_)")
        private val INLINE_RULES = listOf(
            InlineRule(INLINE_CODE_PATTERN, Function { match -> wrap("code", match.group(1)) }),
            InlineRule(BOLD_PATTERN, Function { match -> wrap("b", match.group(1)) }),
            InlineRule(SPOILER_PATTERN, Function { match -> wrap("tg-spoiler", match.group(1)) }),
            InlineRule(STRIKETHROUGH_PATTERN, Function { match -> wrap("s", match.group(1)) }),
            InlineRule(ITALIC_PATTERN, Function(::formatItalicMatch)),
        )

        private fun stripSingleLeadingNewline(text: String?): String {
            if (text.isNullOrEmpty()) {
                return ""
            }
            if (text.startsWith("\r\n")) {
                return text.substring(2)
            }
            if (text.startsWith("\n")) {
                return text.substring(1)
            }
            return text
        }

        private fun renderCodeBlock(text: String?): String =
            "<pre><code>${escapeHtml(stripSingleLeadingNewline(text))}</code></pre>"

        private fun formatInlineSegment(text: String?): String {
            if (text.isNullOrEmpty()) {
                return ""
            }
            return applyRules(text, 0)
        }

        private fun applyRules(text: String, ruleIndex: Int): String {
            if (ruleIndex >= INLINE_RULES.size) {
                return escapeHtml(text)
            }
            return applyRule(text, INLINE_RULES[ruleIndex], ruleIndex)
        }

        private fun applyRule(text: String, rule: InlineRule, ruleIndex: Int): String {
            val formatted = StringBuilder()
            val matcher = rule.pattern.matcher(text)
            var cursor = 0
            while (matcher.find()) {
                formatted.append(applyRules(text.substring(cursor, matcher.start()), ruleIndex + 1))
                formatted.append(rule.replacement.apply(matcher))
                cursor = matcher.end()
            }
            formatted.append(applyRules(text.substring(cursor), ruleIndex + 1))
            return formatted.toString()
        }

        private fun formatItalicMatch(matcher: Matcher): String {
            val marker = matcher.group()
            val content = matcher.group(1)
            if (!shouldFormatItalic(marker, content)) {
                return marker
            }
            return wrap("i", content)
        }

        private fun shouldFormatItalic(marker: String?, content: String?): Boolean {
            if (content.isNullOrBlank()) {
                return false
            }
            if (marker != null && marker.startsWith("__") && marker.endsWith("__")) {
                return true
            }
            for (char in content) {
                if (char.isLetter()) {
                    return true
                }
            }
            return false
        }

        private fun wrap(tag: String, content: String?): String =
            "<$tag>${escapeHtml(content)}</$tag>"

        private fun escapeHtml(text: String?): String =
            text?.replace("&", "&amp;")
                ?.replace("<", "&lt;")
                ?.replace(">", "&gt;")
                ?: ""
    }
}
