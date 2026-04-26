package com.telegram.codex.conversation.application.reply

import com.telegram.codex.conversation.application.ProcessedUpdateService
import com.telegram.codex.conversation.application.gateway.ReplyGenerationGateway
import com.telegram.codex.conversation.infrastructure.memory.ChatMemoryRepository
import com.telegram.codex.conversation.infrastructure.memory.CodexMemoryClient
import com.telegram.codex.conversation.infrastructure.session.ChatSessionRepository
import com.telegram.codex.conversation.application.session.SessionService
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway
import com.telegram.codex.integration.telegram.domain.InboundMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.stream.Stream

data class ReplyResult(
    val conversationState: String?,
    val suggestedReplies: List<String>,
    val text: String,
)

@Component
class AttachmentDownloader(
    private val telegramClient: TelegramGateway,
) {
    fun downloadImages(imageFileIds: List<String>): List<Path> {
        val imagePaths = ArrayList<Path>()
        for (imageFileId in imageFileIds) {
            imagePaths.add(telegramClient.downloadFileToTemp(imageFileId))
        }
        return imagePaths.toList()
    }

    fun cleanup(filePaths: List<Path?>) {
        val uniqueParentDirs = HashSet<Path>()
        for (filePath in filePaths) {
            if (filePath?.parent != null) {
                uniqueParentDirs.add(filePath.parent)
            }
        }
        for (parentDir in uniqueParentDirs) {
            deleteDirectoryRecursively(parentDir)
        }
    }

    private fun deleteDirectoryRecursively(directory: Path?) {
        if (directory == null || !Files.exists(directory)) {
            return
        }
        try {
            Files.walk(directory).use { paths: Stream<Path> ->
                paths.sorted(Comparator.reverseOrder()).forEach { path ->
                    try {
                        Files.deleteIfExists(path)
                    } catch (error: IOException) {
                        LOGGER.warn("Failed to delete file: {}", path, error)
                    }
                }
            }
        } catch (error: IOException) {
            LOGGER.warn("Failed to walk directory for deletion: {}", directory, error)
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(AttachmentDownloader::class.java)
    }
}

@Service
class ReplyGenerationService(
    private val replyClient: ReplyGenerationGateway,
    private val chatSessionRepository: ChatSessionRepository,
    private val chatMemoryRepository: ChatMemoryRepository,
    private val memoryClient: CodexMemoryClient,
    private val processedUpdateService: ProcessedUpdateService,
    private val sessionService: SessionService,
    private val telegramClient: TelegramGateway,
    private val attachmentDownloader: AttachmentDownloader,
) {
    fun handle(message: InboundMessage) {
        try {
            processedUpdateService.pruneIfNeeded()
            val reply = generateReplyWithTypingStatus(message)
            deliverReply(message, reply)
        } catch (error: Exception) {
            processedUpdateService.clearProcessing(message.updateId)
            throw error
        }
    }

    private fun generateReplyWithTypingStatus(message: InboundMessage): ReplyResult =
        telegramClient.withTypingStatus(message.chatId) { generateReply(message) }

    private fun generateReply(message: InboundMessage): ReplyResult {
        val imageFilePaths = attachmentDownloader.downloadImages(message.effectiveImageFileIds())
        try {
            // 冇新圖但 reply-to 有圖時，effectiveImageFileIds() 會退回引用圖片，令模型仍然見到上下文。
            return replyClient.generateReply(
                message.textOrEmpty(),
                findLastResponseId(message.chatId),
                imageFilePaths,
                message.replyToText,
                findMemoryText(message.chatId),
            )
        } finally {
            attachmentDownloader.cleanup(imageFilePaths)
        }
    }

    private fun deliverReply(message: InboundMessage, reply: ReplyResult) {
        // 先落 pending reply，再 send Telegram；咁中途重試時可以重播未完成回覆。
        processedUpdateService.savePendingReply(message.updateId, message.chatId, message.messageId, reply)
        telegramClient.sendMessage(message.chatId, reply.text, reply.suggestedReplies, false)
        sessionService.persistConversationState(message.chatId, reply.conversationState)
        refreshMemory(message.chatId, message.text, reply.text)
        processedUpdateService.markProcessed(message.updateId, message.chatId, message.messageId)
    }

    private fun findLastResponseId(chatId: String): String? =
        chatSessionRepository.findActive(chatId).map { it.lastResponseId }.orElse(null)

    private fun findMemoryText(chatId: String): String? =
        chatMemoryRepository.find(chatId).map { it.memoryText }.orElse(null)

    private fun refreshMemory(chatId: String, userMessage: String?, assistantReply: String) {
        if (userMessage.isNullOrBlank()) {
            return
        }
        try {
            persistMemory(chatId, userMessage, assistantReply)
        } catch (error: Exception) {
            LOGGER.warn("Failed to refresh long-term memory chat_id={} error={}", chatId, error.message)
        }
    }

    private fun persistMemory(chatId: String, userMessage: String, assistantReply: String) {
        val existingMemory = chatMemoryRepository.find(chatId).map { it.memoryText }.orElse("")
        val mergedMemory = memoryClient.merge(existingMemory, userMessage, assistantReply)
        if (mergedMemory != existingMemory) {
            chatMemoryRepository.persist(chatId, mergedMemory)
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(ReplyGenerationService::class.java)
    }
}
