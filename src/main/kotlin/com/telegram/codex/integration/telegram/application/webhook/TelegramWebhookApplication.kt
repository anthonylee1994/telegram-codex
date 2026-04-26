package com.telegram.codex.integration.telegram.application.webhook

import com.telegram.codex.conversation.application.JobSchedulerService
import com.telegram.codex.conversation.application.ProcessedUpdateService
import com.telegram.codex.conversation.application.session.SessionService
import com.telegram.codex.conversation.domain.ChatRateLimiter
import com.telegram.codex.conversation.domain.ConversationConstants
import com.telegram.codex.conversation.domain.MessageConstants
import com.telegram.codex.conversation.infrastructure.MediaGroupBufferRepository
import com.telegram.codex.integration.telegram.application.CompactResultSender
import com.telegram.codex.integration.telegram.application.port.`in`.TelegramMessageParser
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway
import com.telegram.codex.integration.telegram.domain.InboundMessage
import com.telegram.codex.integration.telegram.domain.webhook.TelegramUpdate
import com.telegram.codex.shared.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.Optional
import java.util.regex.Pattern

@Component
class CompactCommandExecutor(
    private val sessionService: SessionService,
    private val jobSchedulerService: JobSchedulerService,
    private val responder: TelegramCommandResponder,
) {
    fun execute(message: InboundMessage) {
        val snapshot = sessionService.snapshot(message.chatId)
        val immediateResult = validate(snapshot)
        if (immediateResult.isPresent) {
            responder.sendCompactResult(message, immediateResult.get())
            return
        }
        jobSchedulerService.enqueueSessionCompact(message.chatId)
        responder.reply(message, MessageConstants.COMPACT_QUEUED_MESSAGE)
    }

    private fun validate(snapshot: SessionService.SessionSnapshot): Optional<SessionService.SessionCompactResult> {
        if (!snapshot.active) {
            return Optional.of(SessionService.SessionCompactResult.missingSession())
        }
        if (snapshot.messageCount < ConversationConstants.MIN_TRANSCRIPT_SIZE_FOR_COMPACT) {
            return Optional.of(SessionService.SessionCompactResult.tooShort(snapshot.messageCount))
        }
        return Optional.empty()
    }
}

@Component
class DuplicateUpdateHandler(
    private val processedUpdateService: ProcessedUpdateService,
    private val telegramClient: TelegramGateway,
) {
    fun handle(message: InboundMessage): Boolean {
        val processedUpdate = processedUpdateService.find(message.updateId)
        if (processedUpdateService.duplicate(processedUpdate)) {
            LOGGER.info("Ignored duplicate update update_id={}", message.updateId)
            return true
        }
        if (processedUpdateService.replayable(processedUpdate)) {
            processedUpdateService.resendPendingReply(message, processedUpdate.orElseThrow(), telegramClient)
            return true
        }
        return false
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DuplicateUpdateHandler::class.java)
    }
}

@Component
class InboundMessageProcessor(
    private val unsupportedMessageHandler: UnsupportedMessageHandler,
    private val duplicateUpdateHandler: DuplicateUpdateHandler,
    private val telegramCommandHandler: TelegramCommandHandler,
    private val replyRequestGuard: ReplyRequestGuard,
    private val mediaGroupStore: MediaGroupBufferRepository,
    private val jobSchedulerService: JobSchedulerService,
) {
    private val processingSteps = listOf<ProcessingStep>(
        ProcessingStep { message, update -> handleUnsupportedMessage(message, update) },
        ProcessingStep { message, update -> handleDuplicateUpdate(message, update) },
        ProcessingStep { message, update -> handleTelegramCommand(message, update) },
        ProcessingStep { message, update -> handleBlockedReplyRequest(message, update) },
    )

    fun process(message: InboundMessage) {
        process(message, null)
    }

    fun process(message: InboundMessage?, update: TelegramUpdate?) {
        if (message == null) {
            unsupportedMessageHandler.handle(null, update)
            return
        }
        if (handledByProcessingStep(message, update)) {
            return
        }
        jobSchedulerService.enqueueReplyGeneration(message)
    }

    fun deferMediaGroup(message: InboundMessage, waitDuration: Duration) {
        val result = mediaGroupStore.enqueue(message, waitDuration.toMillis() / 1000.0)
        jobSchedulerService.scheduleMediaGroupFlush(result.key, result.deadlineAt, waitDuration)
    }

    private fun handledByProcessingStep(message: InboundMessage, update: TelegramUpdate?): Boolean {
        for (step in processingSteps) {
            if (step.handle(message, update)) {
                return true
            }
        }
        return false
    }

    private fun handleUnsupportedMessage(message: InboundMessage, update: TelegramUpdate?): Boolean =
        unsupportedMessageHandler.handle(message, update)

    private fun handleDuplicateUpdate(message: InboundMessage, update: TelegramUpdate?): Boolean =
        duplicateUpdateHandler.handle(message)

    private fun handleTelegramCommand(message: InboundMessage, update: TelegramUpdate?): Boolean =
        telegramCommandHandler.handle(message)

    private fun handleBlockedReplyRequest(message: InboundMessage, update: TelegramUpdate?): Boolean =
        !replyRequestGuard.allow(message)

    private fun interface ProcessingStep {
        fun handle(message: InboundMessage, update: TelegramUpdate?): Boolean
    }
}

@Component
class ReplyRequestGuard(
    private val properties: AppProperties,
    private val rateLimiter: ChatRateLimiter,
    private val processedUpdateService: ProcessedUpdateService,
    private val sensitiveIntentGuard: SensitiveIntentGuard,
    private val telegramClient: TelegramGateway,
) {
    fun allow(message: InboundMessage): Boolean =
        allowAuthorizedUser(message) &&
            allowSupportedMediaGroupSize(message) &&
            allowSafeIntent(message) &&
            allowChatRate(message) &&
            beginProcessing(message)

    private fun sendAndMarkProcessed(message: InboundMessage, text: String) {
        telegramClient.sendMessage(message.chatId, text, emptyList(), false)
        processedUpdateService.markProcessed(message)
    }

    private fun allowAuthorizedUser(message: InboundMessage): Boolean {
        if (properties.allowedTelegramUserIds().isEmpty() || properties.allowedTelegramUserIds().contains(message.userId)) {
            return true
        }
        LOGGER.warn("Rejected unauthorized Telegram user chat_id={} user_id={}", message.chatId, message.userId)
        sendAndMarkProcessed(message, MessageConstants.UNAUTHORIZED_MESSAGE)
        return false
    }

    private fun allowSupportedMediaGroupSize(message: InboundMessage): Boolean {
        if (!message.mediaGroup() || message.imageCount() <= properties.maxMediaGroupImages) {
            return true
        }
        sendAndMarkProcessed(message, MessageConstants.TOO_MANY_IMAGES_MESSAGE)
        return false
    }

    private fun allowSafeIntent(message: InboundMessage): Boolean {
        if (!sensitiveIntentGuard.shouldBlock(message)) {
            return true
        }
        sendAndMarkProcessed(message, MessageConstants.SENSITIVE_INTENT_MESSAGE)
        return false
    }

    private fun allowChatRate(message: InboundMessage): Boolean {
        if (rateLimiter.allow(message.chatId)) {
            return true
        }
        sendAndMarkProcessed(message, MessageConstants.RATE_LIMIT_MESSAGE)
        return false
    }

    private fun beginProcessing(message: InboundMessage): Boolean {
        if (processedUpdateService.beginProcessing(message)) {
            return true
        }
        LOGGER.info("Ignored duplicate update update_id={}", message.updateId)
        return false
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ReplyRequestGuard::class.java)
    }
}

@Component
class SensitiveIntentGuard {
    fun shouldBlock(message: InboundMessage?): Boolean {
        if (message == null) {
            return false
        }
        return containsSensitivePattern(message.text) || containsSensitivePattern(message.replyToText)
    }

    private fun containsSensitivePattern(value: String?): Boolean {
        if (value.isNullOrBlank()) {
            return false
        }
        val normalized = value.lowercase()
        return SENSITIVE_PATTERNS.any { normalized.contains(it) }
    }

    companion object {
        private val SENSITIVE_PATTERNS = listOf(
            "code base", "codebase", "repo", "repository", "source code", "project files",
            "local files", "internal files", "system prompt", "hidden prompt", "hidden instructions",
            "scan the project", "scan project", "inspect the repo", "read the repo", "review the repo",
            "find bugs in your code", "bugs in your code", "look through the codebase",
            "睇下你個 code base", "睇下你 code base", "你 code base", "你個 repo", "你 repo",
            "內部檔案", "本機檔案", "source code 有咩 bugs", "程式碼有咩 bugs", "系統提示", "隱藏指示",
        )
    }
}

@Component
class TelegramCommandHandler(
    private val commandRegistry: TelegramCommandRegistry,
    private val sessionService: SessionService,
    private val messageBuilder: TelegramStatusMessageBuilder,
    private val responder: TelegramCommandResponder,
    private val compactCommandExecutor: CompactCommandExecutor,
) {
    fun handle(message: InboundMessage): Boolean {
        val command = commandRegistry.resolve(message)
        if (command.isEmpty) {
            return false
        }
        execute(command.get(), message)
        return true
    }

    private fun execute(command: TelegramCommandRegistry.TelegramCommand, message: InboundMessage) {
        when (command) {
            TelegramCommandRegistry.TelegramCommand.START -> {
                sessionService.reset(message.chatId)
                responder.reply(message, MessageConstants.START_MESSAGE)
            }
            TelegramCommandRegistry.TelegramCommand.NEW_SESSION -> {
                sessionService.reset(message.chatId)
                responder.reply(message, MessageConstants.NEW_SESSION_MESSAGE)
            }
            TelegramCommandRegistry.TelegramCommand.HELP -> responder.reply(message, MessageConstants.HELP_MESSAGE)
            TelegramCommandRegistry.TelegramCommand.STATUS -> responder.reply(message, messageBuilder.buildStatusMessage(message.chatId))
            TelegramCommandRegistry.TelegramCommand.SESSION -> responder.reply(message, messageBuilder.buildSessionMessage(message.chatId))
            TelegramCommandRegistry.TelegramCommand.MEMORY -> responder.reply(message, messageBuilder.buildMemoryMessage(message.chatId))
            TelegramCommandRegistry.TelegramCommand.FORGET -> {
                sessionService.resetMemory(message.chatId)
                responder.reply(message, MessageConstants.RESET_MEMORY_MESSAGE)
            }
            TelegramCommandRegistry.TelegramCommand.COMPACT -> compactCommandExecutor.execute(message)
        }
    }
}

@Component
class TelegramCommandRegistry {
    fun resolve(message: InboundMessage): Optional<TelegramCommand> =
        COMMANDS.firstOrNull { it.matches(message.textOrEmpty()) }?.let { Optional.of(it.command) } ?: Optional.empty()

    enum class TelegramCommand {
        START,
        NEW_SESSION,
        HELP,
        STATUS,
        SESSION,
        MEMORY,
        FORGET,
        COMPACT,
    }

    private data class CommandSpec(
        val pattern: Pattern,
        val command: TelegramCommand,
    ) {
        fun matches(text: String): Boolean = pattern.matcher(text).matches()
    }

    companion object {
        private val COMMANDS = listOf(
            CommandSpec(Pattern.compile("^/start(?:@[\\w_]+)?$", Pattern.UNICODE_CASE), TelegramCommand.START),
            CommandSpec(Pattern.compile("^/new(?:@[\\w_]+)?$", Pattern.UNICODE_CASE), TelegramCommand.NEW_SESSION),
            CommandSpec(Pattern.compile("^/help(?:@[\\w_]+)?$", Pattern.UNICODE_CASE), TelegramCommand.HELP),
            CommandSpec(Pattern.compile("^/status(?:@[\\w_]+)?$", Pattern.UNICODE_CASE), TelegramCommand.STATUS),
            CommandSpec(Pattern.compile("^/session(?:@[\\w_]+)?$", Pattern.UNICODE_CASE), TelegramCommand.SESSION),
            CommandSpec(Pattern.compile("^/memory(?:@[\\w_]+)?$", Pattern.UNICODE_CASE), TelegramCommand.MEMORY),
            CommandSpec(Pattern.compile("^/forget(?:@[\\w_]+)?$", Pattern.UNICODE_CASE), TelegramCommand.FORGET),
            CommandSpec(Pattern.compile("^/compact(?:@[\\w_]+)?$", Pattern.UNICODE_CASE), TelegramCommand.COMPACT),
        )
    }
}

@Component
class TelegramCommandResponder(
    private val processedUpdateService: ProcessedUpdateService,
    private val telegramClient: TelegramGateway,
    private val compactResultSender: CompactResultSender,
) {
    fun reply(message: InboundMessage, text: String) {
        telegramClient.sendMessage(message.chatId, text, emptyList(), true)
        processedUpdateService.markProcessed(message)
    }

    fun sendCompactResult(message: InboundMessage, result: SessionService.SessionCompactResult) {
        compactResultSender.send(message.chatId, result)
        processedUpdateService.markProcessed(message)
    }
}

@Component
class TelegramStatusMessageBuilder(
    private val sessionService: SessionService,
) {
    fun buildStatusMessage(chatId: String): String {
        val snapshot = sessionService.snapshot(chatId)
        return listOf(
            "Bot 狀態：OK 🤖",
            "Session 狀態：${renderSessionStatus(snapshot)}",
            "只支持：文字、圖片",
        ).joinToString("\n")
    }

    fun buildSessionMessage(chatId: String): String {
        val snapshot = sessionService.snapshot(chatId)
        if (!snapshot.active) {
            return "目前未有已生效 session。你可以直接 send 訊息開始，或者之後打 /compact 壓縮長對話。"
        }
        return listOf(
            "目前 session：已生效",
            "訊息數：${snapshot.messageCount}",
            "大概輪數：${snapshot.turnCount}",
            "最後更新：${snapshot.lastUpdatedAt}",
            "想壓縮 context 可以打 /compact。",
        ).joinToString("\n")
    }

    fun buildMemoryMessage(chatId: String): String {
        val snapshot = sessionService.memorySnapshot(chatId)
        if (!snapshot.active) {
            return "目前未有長期記憶。你可以直接叫我記住、改寫或者刪除長期記憶；我之後亦會自動記低穩定偏好同持續背景。想清除可以打 /forget。"
        }
        return listOf(
            "長期記憶：已生效",
            "最後更新：${snapshot.lastUpdatedAt}",
            "",
            snapshot.memoryText,
            "",
            "你可以直接叫我改寫或者刪除長期記憶。",
            "想清除可以打 /forget。",
        ).joinToString("\n")
    }

    private fun renderSessionStatus(snapshot: SessionService.SessionSnapshot): String =
        if (snapshot.active) "已生效" else "未生效"
}

@Component
class TelegramWebhookHandler(
    private val telegramUpdateParser: TelegramMessageParser,
    private val webhookRouter: TelegramWebhookRouter,
) {
    fun handle(update: TelegramUpdate?) {
        // Handler 只做 parse + handoff；真正 routing 留返俾 router，避免呢層再塞入業務判斷。
        val message = telegramUpdateParser.parseIncomingTelegramMessage(update)
        webhookRouter.route(message, update)
    }
}

@Component
class TelegramWebhookRouter(
    private val properties: AppProperties,
    private val inboundMessageProcessor: InboundMessageProcessor,
) {
    fun route(message: InboundMessage?, update: TelegramUpdate?) {
        if (shouldDeferMediaGroup(message)) {
            inboundMessageProcessor.deferMediaGroup(message!!, Duration.ofMillis(properties.mediaGroupWaitMs.toLong()))
            return
        }
        inboundMessageProcessor.process(message, update)
    }

    private fun shouldDeferMediaGroup(message: InboundMessage?): Boolean = message != null && message.mediaGroup()
}

@Component
class UnsupportedMessageHandler(
    private val telegramClient: TelegramGateway,
) {
    fun handle(message: InboundMessage?, update: TelegramUpdate?): Boolean {
        if (message != null && !message.unsupported()) {
            return false
        }
        if (update == null) {
            return true
        }
        val chatId = extractChatId(update)
        if (chatId != null) {
            telegramClient.sendMessage(chatId, MessageConstants.UNSUPPORTED_MESSAGE, emptyList(), false)
        }
        return true
    }

    private fun extractChatId(update: TelegramUpdate): String? {
        val message = update.message
        if (message?.chat?.id == null) {
            return null
        }
        return message.chat.id.toString()
    }
}
