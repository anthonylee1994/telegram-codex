package com.telegram.codex.conversation.infrastructure

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "chat_memories")
class ChatMemoryEntity {
    @Id
    @Column(name = "chat_id", nullable = false)
    var chatId: String? = null

    @Column(name = "memory_text", columnDefinition = "text")
    var memoryText: String? = null

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Long = 0
}

interface ChatMemoryJpaRepository : JpaRepository<ChatMemoryEntity, String>

@Entity
@Table(name = "chat_sessions")
class ChatSessionEntity {
    @Id
    @Column(name = "chat_id", nullable = false)
    var chatId: String? = null

    @Column(name = "last_response_id", columnDefinition = "text")
    var lastResponseId: String? = null

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Long = 0
}

interface ChatSessionJpaRepository : JpaRepository<ChatSessionEntity, String>

@Entity
@Table(name = "media_group_buffers")
class MediaGroupBufferEntity {
    @Id
    @Column(name = "key", nullable = false)
    var key: String? = null

    @Column(name = "deadline_at", nullable = false)
    var deadlineAt: Long = 0
}

interface MediaGroupBufferJpaRepository : JpaRepository<MediaGroupBufferEntity, String>

@Entity
@Table(name = "media_group_messages")
class MediaGroupMessageEntity {
    @Id
    @Column(name = "update_id", nullable = false)
    var updateId: Long? = null

    @Column(name = "media_group_key", nullable = false, columnDefinition = "text")
    var mediaGroupKey: String? = null

    @Column(name = "message_id", nullable = false)
    var messageId: Long = 0

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    var payload: String? = null
}

interface MediaGroupMessageJpaRepository : JpaRepository<MediaGroupMessageEntity, Long> {
    fun findByMediaGroupKeyOrderByMessageIdAscUpdateIdAsc(mediaGroupKey: String): List<MediaGroupMessageEntity>
    fun deleteByMediaGroupKey(mediaGroupKey: String)
}

@Entity
@Table(name = "processed_updates")
class ProcessedUpdateEntity {
    @Id
    @Column(name = "update_id", nullable = false)
    var updateId: Long? = null

    @Column(name = "chat_id", nullable = false)
    var chatId: String? = null

    @Column(name = "message_id", nullable = false)
    var messageId: Long? = null

    @Column(name = "processed_at", nullable = false)
    var processedAt: Long = 0

    @Column(name = "reply_text", columnDefinition = "text")
    var replyText: String? = null

    @Column(name = "conversation_state", columnDefinition = "text")
    var conversationState: String? = null

    @Column(name = "suggested_replies", columnDefinition = "text")
    var suggestedReplies: String? = null

    @Column(name = "sent_at")
    var sentAt: Long? = null
}

interface ProcessedUpdateJpaRepository : JpaRepository<ProcessedUpdateEntity, Long> {
    fun deleteBySentAtIsNotNullAndProcessedAtLessThan(cutoff: Long): Long
}
