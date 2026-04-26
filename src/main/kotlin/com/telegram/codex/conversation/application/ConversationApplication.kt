package com.telegram.codex.conversation.application

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.telegram.codex.conversation.application.reply.ReplyGenerationService
import com.telegram.codex.conversation.application.reply.ReplyResult
import com.telegram.codex.conversation.application.session.SessionService
import com.telegram.codex.conversation.domain.ConversationConstants
import com.telegram.codex.conversation.domain.update.ProcessedUpdateRecord
import com.telegram.codex.conversation.infrastructure.MediaGroupBufferRepository
import com.telegram.codex.conversation.infrastructure.update.ProcessedUpdateRepository
import com.telegram.codex.integration.telegram.application.CompactResultSender
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway
import com.telegram.codex.integration.telegram.application.webhook.InboundMessageProcessor
import com.telegram.codex.integration.telegram.domain.InboundMessage
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.Optional
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToLong

@Service
class JobSchedulerService(
    private val mediaGroupStore: MediaGroupBufferRepository,
    private val inboundMessageProcessorProvider: ObjectProvider<InboundMessageProcessor>,
    private val replyGenerationService: ReplyGenerationService,
    private val sessionService: SessionService,
    private val compactResultSender: CompactResultSender,
) {
    private val taskExecutor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()
    private val scheduledExecutorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
        Thread.ofPlatform().name("media-group-scheduler-", 0).daemon(true).factory(),
    )

    fun enqueueReplyGeneration(message: InboundMessage) {
        taskExecutor.execute { replyGenerationService.handle(message) }
    }

    fun scheduleMediaGroupFlush(key: String, expectedDeadlineAt: Long, waitDuration: Duration) {
        scheduledExecutorService.schedule(
            { taskExecutor.execute { flushMediaGroup(key, expectedDeadlineAt) } },
            waitDuration.toMillis(),
            TimeUnit.MILLISECONDS,
        )
    }

    fun enqueueSessionCompact(chatId: String) {
        taskExecutor.execute {
            val result = sessionService.compact(chatId)
            compactResultSender.send(chatId, result)
        }
    }

    @PreDestroy
    fun shutdown() {
        scheduledExecutorService.shutdown()
        taskExecutor.shutdown()
    }

    private fun flushMediaGroup(key: String, expectedDeadlineAt: Long) {
        val result = mediaGroupStore.flush(key, expectedDeadlineAt)
        when (result.status) {
            "ready" -> inboundMessageProcessorProvider.getObject().process(result.message!!)
            "pending" -> scheduleMediaGroupFlush(
                key,
                expectedDeadlineAt,
                Duration.ofMillis((result.waitDurationSeconds!! * 1000.0).roundToLong()),
            )
        }
    }
}

@Service
class ProcessedUpdateService(
    private val objectMapper: ObjectMapper,
    private val processedUpdateRepository: ProcessedUpdateRepository,
    private val sessionService: SessionService,
) {
    private val lastProcessedUpdatePruneAt = AtomicLong(0)

    fun find(updateId: Long): Optional<ProcessedUpdateRecord> = processedUpdateRepository.find(updateId)

    fun beginProcessing(message: InboundMessage): Boolean {
        val claimedUpdateIds = ArrayList<Long>()
        for (processingUpdate in message.processingUpdates) {
            val claimed = processedUpdateRepository.beginProcessing(
                processingUpdate.updateId,
                message.chatId,
                processingUpdate.messageId,
            )
            if (!claimed) {
                rollbackProcessingClaims(claimedUpdateIds)
                return false
            }
            claimedUpdateIds.add(processingUpdate.updateId)
        }
        return true
    }

    fun clearProcessing(updateId: Long) {
        processedUpdateRepository.clearProcessing(updateId)
    }

    fun duplicate(processedUpdate: Optional<ProcessedUpdateRecord>): Boolean =
        processedUpdate.map(ProcessedUpdateRecord::sentAt).orElse(null) != null

    fun replayable(processedUpdate: Optional<ProcessedUpdateRecord>): Boolean =
        processedUpdate.filter { it.replyText != null && it.conversationState != null }.isPresent

    fun resendPendingReply(message: InboundMessage, processedUpdate: ProcessedUpdateRecord, telegramClient: TelegramGateway) {
        telegramClient.sendMessage(
            message.chatId,
            processedUpdate.replyText,
            parseStoredSuggestedReplies(processedUpdate.suggestedReplies),
            false,
        )
        sessionService.persistConversationState(message.chatId, processedUpdate.conversationState)
        markProcessed(message)
    }

    fun markProcessed(updateId: Long, chatId: String, messageId: Long) {
        processedUpdateRepository.markProcessed(updateId, chatId, messageId)
    }

    fun markProcessed(message: InboundMessage) {
        for (processingUpdate in message.processingUpdates) {
            processedUpdateRepository.markProcessed(processingUpdate.updateId, message.chatId, processingUpdate.messageId)
        }
    }

    fun savePendingReply(updateId: Long, chatId: String, messageId: Long, result: ReplyResult) {
        processedUpdateRepository.savePendingReply(updateId, chatId, messageId, result)
    }

    fun pruneIfNeeded() {
        val now = System.currentTimeMillis()
        val lastPrunedAt = lastProcessedUpdatePruneAt.get()
        if (lastPrunedAt != 0L && now - lastPrunedAt < ConversationConstants.PROCESSED_UPDATE_PRUNE_INTERVAL_MS) {
            return
        }
        val cutoff = now - ConversationConstants.PROCESSED_UPDATE_RETENTION_MS
        val deletedCount = processedUpdateRepository.pruneSentBefore(cutoff)
        lastProcessedUpdatePruneAt.set(now)
        LOGGER.info("Pruned processed updates count={} cutoff={}", deletedCount, cutoff)
    }

    private fun rollbackProcessingClaims(claimedUpdateIds: List<Long>) {
        for (updateId in claimedUpdateIds) {
            processedUpdateRepository.clearProcessing(updateId)
        }
    }

    internal fun parseStoredSuggestedReplies(rawSuggestedReplies: String?): List<String> {
        if (rawSuggestedReplies.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            val replies = objectMapper.readValue(rawSuggestedReplies, STRING_LIST)
            replies?.filter { !it.isNullOrBlank() }?.map { it.trim() } ?: emptyList()
        } catch (error: Exception) {
            emptyList()
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ProcessedUpdateService::class.java)
        private val STRING_LIST = object : TypeReference<List<String>>() {}
    }
}
