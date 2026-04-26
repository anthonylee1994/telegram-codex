package com.telegram.codex.conversation.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.telegram.codex.conversation.domain.*
import com.telegram.codex.conversation.infrastructure.ChatMemoryRepository
import com.telegram.codex.conversation.infrastructure.ChatSessionRepository
import com.telegram.codex.conversation.infrastructure.CodexSessionCompactClient
import org.springframework.stereotype.Service

@Service
class SessionService(
    private val chatSessionRepository: ChatSessionRepository,
    private val sessionCompactClient: CodexSessionCompactClient,
    private val objectMapper: ObjectMapper,
    private val chatMemoryRepository: ChatMemoryRepository,
) {
    fun persistConversationState(chatId: String, conversationState: String?) {
        chatSessionRepository.persist(chatId, conversationState)
    }

    fun reset(chatId: String) {
        chatSessionRepository.reset(chatId)
    }

    fun snapshot(chatId: String): SessionSnapshot {
        val maybeSession = chatSessionRepository.findActive(chatId)
        if (maybeSession.isEmpty) {
            return SessionSnapshot.inactive()
        }
        val session = maybeSession.get()
        val transcript = readTranscript(session)
        return SessionSnapshot.active(
            transcript.size(),
            kotlin.math.ceil(transcript.size() / 2.0).toInt(),
            ConversationTimeFormatter.format(session.updatedAt),
        )
    }

    fun compact(chatId: String): SessionCompactResult {
        val maybeSession = chatSessionRepository.findActive(chatId)
        if (maybeSession.isEmpty) {
            return SessionCompactResult.missingSession()
        }
        val transcript = readTranscript(maybeSession.get())
        if (transcript.size() < ConversationConstants.MIN_TRANSCRIPT_SIZE_FOR_COMPACT) {
            return SessionCompactResult.tooShort(transcript.size())
        }
        val compactText = sessionCompactClient.compact(transcript)
        persistCompactTranscript(chatId, compactText)
        return SessionCompactResult.ok(transcript.size(), compactText)
    }

    fun memorySnapshot(chatId: String): MemorySnapshot {
        val maybeMemory = chatMemoryRepository.find(chatId)
        if (maybeMemory.isEmpty) {
            return MemorySnapshot.inactive()
        }
        val memory = maybeMemory.get()
        if (memory.memoryText.isNullOrBlank()) {
            return MemorySnapshot.inactive()
        }
        return MemorySnapshot.active(
            memory.memoryText,
            ConversationTimeFormatter.format(memory.updatedAt),
        )
    }

    fun resetMemory(chatId: String) {
        chatMemoryRepository.reset(chatId)
    }

    private fun readTranscript(session: ChatSessionRecord): Transcript =
        Transcript.fromConversationState(session.lastResponseId, objectMapper)

    private fun persistCompactTranscript(chatId: String, compactText: String) {
        val compactTranscript = Transcript.empty()
            .append("user", MessageConstants.COMPACT_BASELINE_MESSAGE)
            .append("assistant", compactText)
        chatSessionRepository.persist(chatId, compactTranscript.toConversationState(objectMapper))
    }

    data class SessionSnapshot(
        val active: Boolean,
        val messageCount: Int,
        val turnCount: Int,
        val lastUpdatedAt: String?,
    ) {
        companion object {
            fun inactive(): SessionSnapshot = SessionSnapshot(false, 0, 0, null)
            fun active(messageCount: Int, turnCount: Int, lastUpdatedAt: String): SessionSnapshot =
                SessionSnapshot(true, messageCount, turnCount, lastUpdatedAt)
        }
    }

    data class SessionCompactResult(
        val status: Status,
        val messageCount: Int?,
        val originalMessageCount: Int?,
        val compactText: String?,
    ) {
        enum class Status {
            MISSING_SESSION,
            TOO_SHORT,
            OK,
        }

        companion object {
            fun missingSession(): SessionCompactResult = SessionCompactResult(Status.MISSING_SESSION, null, null, null)
            fun tooShort(messageCount: Int): SessionCompactResult = SessionCompactResult(Status.TOO_SHORT, messageCount, null, null)
            fun ok(originalMessageCount: Int, compactText: String): SessionCompactResult =
                SessionCompactResult(Status.OK, null, originalMessageCount, compactText)
        }
    }

    data class MemorySnapshot(
        val active: Boolean,
        val memoryText: String?,
        val lastUpdatedAt: String?,
    ) {
        companion object {
            fun inactive(): MemorySnapshot = MemorySnapshot(false, null, null)
            fun active(memoryText: String, lastUpdatedAt: String): MemorySnapshot = MemorySnapshot(true, memoryText, lastUpdatedAt)
        }
    }
}
