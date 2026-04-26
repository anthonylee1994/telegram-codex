package com.telegram.codex.conversation.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.telegram.codex.conversation.domain.MediaGroupMerger
import com.telegram.codex.conversation.infrastructure.persistence.MediaGroupBufferEntity
import com.telegram.codex.conversation.infrastructure.persistence.MediaGroupBufferJpaRepository
import com.telegram.codex.conversation.infrastructure.persistence.MediaGroupMessageEntity
import com.telegram.codex.conversation.infrastructure.persistence.MediaGroupMessageJpaRepository
import com.telegram.codex.integration.telegram.domain.InboundMessage
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.io.IOException
import kotlin.math.roundToLong

@Service
class MediaGroupBufferRepository(
    private val bufferRepository: MediaGroupBufferJpaRepository,
    private val messageRepository: MediaGroupMessageJpaRepository,
    private val mediaGroupMerger: MediaGroupMerger,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun enqueue(message: InboundMessage, waitDurationSeconds: Double): EnqueueResult {
        val deadlineAt = System.currentTimeMillis() + (waitDurationSeconds * 1000.0).roundToLong()
        val key = buildKey(message)

        val buffer = MediaGroupBufferEntity()
        buffer.key = key
        buffer.deadlineAt = deadlineAt
        bufferRepository.save(buffer)

        val row = MediaGroupMessageEntity()
        row.updateId = message.updateId
        row.mediaGroupKey = key
        row.messageId = message.messageId
        row.payload = writeMessage(message)
        messageRepository.save(row)

        return EnqueueResult(deadlineAt, key)
    }

    @Transactional
    fun flush(key: String, expectedDeadlineAt: Long): FlushResult {
        val buffer = bufferRepository.findById(key).orElse(null) ?: return FlushResult.missing()
        if (buffer.deadlineAt != expectedDeadlineAt) {
            return FlushResult.stale()
        }
        val waitDurationMs = buffer.deadlineAt - System.currentTimeMillis()
        if (waitDurationMs > 0) {
            return FlushResult.pending(waitDurationMs / 1000.0)
        }
        val rows = messageRepository.findByMediaGroupKeyOrderByMessageIdAscUpdateIdAsc(key)
        messageRepository.deleteByMediaGroupKey(key)
        bufferRepository.delete(buffer)
        if (rows.isEmpty()) {
            return FlushResult.missing()
        }
        val messages = rows.map(::readMessage)
        return FlushResult.ready(mediaGroupMerger.merge(messages))
    }

    private fun writeMessage(message: InboundMessage): String =
        try {
            objectMapper.writeValueAsString(message)
        } catch (error: IOException) {
            throw IllegalStateException("Failed to serialize JSON", error)
        }

    private fun readMessage(entity: MediaGroupMessageEntity): InboundMessage =
        try {
            objectMapper.readValue(entity.payload, InboundMessage::class.java)
        } catch (error: IOException) {
            throw IllegalStateException("Failed to deserialize JSON", error)
        }

    private fun buildKey(message: InboundMessage): String = "${message.chatId}:${message.mediaGroupId}"

    data class EnqueueResult(
        val deadlineAt: Long,
        val key: String,
    )

    data class FlushResult(
        val status: String,
        val message: InboundMessage?,
        val waitDurationSeconds: Double?,
    ) {
        companion object {
            fun missing(): FlushResult = FlushResult("missing", null, null)
            fun stale(): FlushResult = FlushResult("stale", null, null)
            fun pending(waitDurationSeconds: Double): FlushResult = FlushResult("pending", null, waitDurationSeconds)
            fun ready(message: InboundMessage): FlushResult = FlushResult("ready", message, null)
        }
    }
}
