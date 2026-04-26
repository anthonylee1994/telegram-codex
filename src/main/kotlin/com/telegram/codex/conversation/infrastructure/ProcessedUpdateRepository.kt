package com.telegram.codex.conversation.infrastructure

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.telegram.codex.conversation.application.ReplyResult
import com.telegram.codex.conversation.domain.ProcessedUpdateRecord
import jakarta.transaction.Transactional
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.*

@Repository
class ProcessedUpdateRepository(
    private val objectMapper: ObjectMapper,
    private val repository: ProcessedUpdateJpaRepository,
) {
    fun find(updateId: Long): Optional<ProcessedUpdateRecord> = repository.findById(updateId).map(::toRecord)

    @Transactional
    fun beginProcessing(updateId: Long, chatId: String, messageId: Long): Boolean {
        val now = System.currentTimeMillis()
        val existing = repository.findById(updateId)
        if (existing.isEmpty) {
            val entity = ProcessedUpdateEntity()
            entity.updateId = updateId
            entity.chatId = chatId
            entity.messageId = messageId
            entity.processedAt = now
            repository.save(entity)
            return true
        }

        val entity = existing.get()
        if (entity.sentAt != null) {
            return false
        }
        if (entity.replyText != null && entity.conversationState != null) {
            return false
        }
        if (now - entity.processedAt < INFLIGHT_TIMEOUT_MS) {
            return false
        }

        entity.chatId = chatId
        entity.messageId = messageId
        entity.processedAt = now
        entity.replyText = null
        entity.conversationState = null
        entity.suggestedReplies = null
        entity.sentAt = null
        repository.save(entity)
        return true
    }

    @Transactional
    fun clearProcessing(updateId: Long) {
        repository.findById(updateId).ifPresent { entity ->
            if (entity.sentAt == null && entity.replyText == null && entity.conversationState == null) {
                repository.delete(entity)
            }
        }
    }

    @Transactional
    fun markProcessed(updateId: Long, chatId: String, messageId: Long) {
        val entity = repository.findById(updateId).orElseGet(::ProcessedUpdateEntity)
        val now = System.currentTimeMillis()
        entity.updateId = updateId
        entity.chatId = chatId
        entity.messageId = messageId
        entity.processedAt = now
        entity.sentAt = now
        repository.save(entity)
    }

    @Transactional
    fun savePendingReply(updateId: Long, chatId: String, messageId: Long, result: ReplyResult) {
        val entity = repository.findById(updateId).orElseGet(::ProcessedUpdateEntity)
        entity.updateId = updateId
        entity.chatId = chatId
        entity.messageId = messageId
        entity.processedAt = System.currentTimeMillis()
        entity.replyText = result.text
        entity.conversationState = result.conversationState
        entity.suggestedReplies = writeSuggestedReplies(result.suggestedReplies)
        entity.sentAt = null
        repository.save(entity)
    }

    fun pruneSentBefore(cutoff: Long): Long = repository.deleteBySentAtIsNotNullAndProcessedAtLessThan(cutoff)

    private fun writeSuggestedReplies(value: Any?): String =
        try {
            objectMapper.writeValueAsString(value)
        } catch (error: JsonProcessingException) {
            throw IllegalStateException("Failed to persist suggested replies", error)
        }

    private fun toRecord(entity: ProcessedUpdateEntity): ProcessedUpdateRecord =
        ProcessedUpdateRecord(
            entity.updateId ?: 0L,
            entity.chatId,
            entity.messageId ?: 0L,
            entity.processedAt,
            entity.replyText,
            entity.conversationState,
            entity.suggestedReplies,
            entity.sentAt,
        )

    companion object {
        private val INFLIGHT_TIMEOUT_MS = Duration.ofMinutes(5).toMillis()
    }
}
