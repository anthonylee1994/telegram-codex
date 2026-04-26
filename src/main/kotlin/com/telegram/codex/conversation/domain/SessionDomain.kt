package com.telegram.codex.conversation.domain

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper

data class ChatSessionRecord(
    val chatId: String?,
    val lastResponseId: String?,
    val updatedAt: Long,
)

class Transcript private constructor(messages: List<Entry>) {
    private val messages: List<Entry> = trim(messages)

    fun append(role: String?, content: String?): Transcript {
        return Transcript(messages + Entry(role ?: "", content ?: ""))
    }

    fun size(): Int = messages.size

    fun toTaggedPromptLines(): List<String> {
        return messages.flatMapIndexed { index, message ->
            listOf(
                "<message index=\"${index + 1}\" role=\"${message.role}\">",
                message.content,
                "</message>",
            )
        }
    }

    fun toConversationState(objectMapper: ObjectMapper): String =
        try {
            objectMapper.writeValueAsString(messages)
        } catch (error: JsonProcessingException) {
            throw IllegalStateException("Failed to serialize JSON", error)
        }

    private fun trim(source: List<Entry>): List<Entry> =
        if (source.size <= ConversationConstants.MAX_TRANSCRIPT_MESSAGES) {
            source.toList()
        } else {
            source.subList(source.size - ConversationConstants.MAX_TRANSCRIPT_MESSAGES, source.size).toList()
        }

    data class Entry(
        @param:JsonProperty("role") val role: String = "",
        @param:JsonProperty("content") val content: String = "",
    )

    companion object {
        fun empty(): Transcript = Transcript(emptyList())

        fun fromConversationState(conversationState: String?, objectMapper: ObjectMapper): Transcript {
            if (conversationState.isNullOrBlank()) {
                return empty()
            }
            return try {
                val payload = objectMapper.readValue(conversationState, object : TypeReference<List<Entry>>() {})
                val messages = payload
                    .filter { it.role in listOf("user", "assistant") }
                    .map { Entry(it.role, it.content) }
                    .filter { it.content.isNotBlank() }
                Transcript(messages)
            } catch (error: JsonProcessingException) {
                empty()
            }
        }
    }
}
