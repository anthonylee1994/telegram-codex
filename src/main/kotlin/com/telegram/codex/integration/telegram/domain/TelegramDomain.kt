package com.telegram.codex.integration.telegram.domain

import com.fasterxml.jackson.annotation.JsonProperty
import com.telegram.codex.conversation.domain.MessageConstants

data class TelegramBotCommand(
    val command: String,
    val description: String,
)

object TelegramConstants {
    const val MAX_SUGGESTED_REPLIES: Int = 3
    const val MAX_SUGGESTED_REPLY_LENGTH: Int = 40
    const val TELEGRAM_API_BASE: String = "https://api.telegram.org/bot"
    const val TELEGRAM_FILE_API_BASE: String = "https://api.telegram.org/file/bot"
}

object TelegramPayloadValueReader {
    fun stringValue(value: Any?): String = value?.toString() ?: ""

    fun blankToNull(value: String?): String? {
        if (value == null) {
            return null
        }
        val normalized = value.trim()
        return normalized.ifEmpty { null }
    }
}

class InboundMessage(
    @param:JsonProperty("chat_id") val chatId: String,
    @JsonProperty("image_file_ids") imageFileIds: List<String>?,
    @JsonProperty("media_group_id") mediaGroupId: String?,
    @param:JsonProperty("message_id") val messageId: Long,
    @JsonProperty("processing_updates") processingUpdates: List<ProcessingUpdate>?,
    @JsonProperty("reply_to_image_file_ids") replyToImageFileIds: List<String>?,
    @JsonProperty("reply_to_text") replyToText: String?,
    @JsonProperty("text") text: String?,
    @param:JsonProperty("user_id") val userId: String,
    @param:JsonProperty("update_id") val updateId: Long,
) {
    val imageFileIds: List<String> = normalizeStrings(imageFileIds)
    val replyToImageFileIds: List<String> = normalizeStrings(replyToImageFileIds)
    val mediaGroupId: String? = normalizeNullableString(mediaGroupId)
    val replyToText: String? = normalizeNullableString(replyToText)
    val text: String? = normalizeNullableString(text)
    val processingUpdates: List<ProcessingUpdate> =
        normalizeProcessingUpdates(processingUpdates, updateId, messageId)

    fun mediaGroup(): Boolean = mediaGroupId != null
    fun unsupported(): Boolean = (text.isNullOrBlank() && imageFileIds.isEmpty())
    fun imageCount(): Int = imageFileIds.size
    fun textOrEmpty(): String = text ?: ""
    fun effectiveImageFileIds(): List<String> =
        imageFileIds.ifEmpty { replyToImageFileIds }

    data class ProcessingUpdate(
        @param:JsonProperty("update_id") val updateId: Long,
        @param:JsonProperty("message_id") val messageId: Long,
    )

    companion object {
        fun forMergedMediaGroup(
            primary: InboundMessage,
            imageFileIds: List<String>,
            processingUpdates: List<ProcessingUpdate>,
            text: String?,
        ): InboundMessage = InboundMessage(
            primary.chatId,
            imageFileIds,
            primary.mediaGroupId,
            primary.messageId,
            processingUpdates,
            emptyList(),
            null,
            text,
            primary.userId,
            primary.updateId,
        )

        fun normalizeStrings(values: List<String>?): List<String> {
            return values.orEmpty()
                .mapNotNull(::normalizeNullableString)
                .distinct()
        }

        private fun normalizeNullableString(value: String?): String? =
            TelegramPayloadValueReader.blankToNull(value)

        private fun normalizeProcessingUpdates(
            updates: List<ProcessingUpdate>?,
            updateId: Long,
            messageId: Long,
        ): List<ProcessingUpdate> {
            val source = if (updates.isNullOrEmpty()) {
                listOf(ProcessingUpdate(updateId, messageId))
            } else {
                updates
            }
            return source
                .sortedWith(compareBy<ProcessingUpdate> { it.messageId }.thenBy { it.updateId })
        }
    }
}

class MessageExtractor(
    private val message: TelegramMessage,
) {
    fun getChatId(): String =
        TelegramPayloadValueReader.stringValue(getChat()?.id)

    fun getMessageId(): Long = message.messageId ?: 0L

    fun getUserId(): String =
        TelegramPayloadValueReader.stringValue(getFrom()?.id)

    fun getMediaGroupId(): String? =
        TelegramPayloadValueReader.blankToNull(TelegramPayloadValueReader.stringValue(message.mediaGroupId))

    fun getText(): String? {
        val text = textValue("text")
        if (text.isNotBlank()) {
            return TelegramPayloadValueReader.blankToNull(text)
        }
        return TelegramPayloadValueReader.blankToNull(textValue("caption"))
    }

    fun getImageFileIds(): List<String> =
        imageDocumentFileId()?.let { listOf(it) }
            ?: getPhotoFileId()?.let { listOf(it) }
            ?: emptyList()

    fun getReplyToMessage(): MessageExtractor? = message.replyToMessage?.let(::MessageExtractor)

    fun getReplyToText(): String? = getReplyToMessage()?.let(::resolveReplyToText)

    fun hasPhoto(): Boolean = getPhotos().isNotEmpty()

    fun isSupported(): Boolean =
        message.text != null || hasPhoto() || imageDocumentFileId() != null

    private fun getChat(): TelegramChat? = message.chat
    private fun getFrom(): TelegramUser? = message.from
    private fun getDocument(): TelegramDocument? = message.document
    private fun getPhotos(): List<TelegramPhotoSize> = message.photo ?: emptyList()

    private fun textValue(key: String): String =
        when (key) {
            "text" -> TelegramPayloadValueReader.stringValue(message.text)
            "caption" -> TelegramPayloadValueReader.stringValue(message.caption)
            else -> ""
        }

    private fun imageDocumentFileId(): String? =
        getDocument()
            ?.takeIf(::isImageDocument)
            ?.let { TelegramPayloadValueReader.stringValue(it.fileId) }

    private fun resolveReplyToText(reply: MessageExtractor): String? {
        val text = reply.getText()
        if (text != null) {
            return text
        }
        if (reply.hasPhoto()) {
            return MessageConstants.REPLY_TO_IMAGE
        }
        if (reply.imageDocumentFileId() != null) {
            return MessageConstants.REPLY_TO_IMAGE_DOCUMENT
        }
        return null
    }

    private fun getPhotoFileId(): String? =
        getPhotos().maxByOrNull { it.fileSize ?: 0L }
            ?.let { photo -> TelegramPayloadValueReader.stringValue(photo.fileId) }

    private fun isImageDocument(document: TelegramDocument?): Boolean {
        if (document?.fileId == null) {
            return false
        }
        val mimeType = TelegramPayloadValueReader.stringValue(document.mimeType).lowercase()
        if (DocumentConstants.IMAGE_MIME_TYPE_PREFIXES.any { mimeType.startsWith(it) }) {
            return true
        }
        val fileName = TelegramPayloadValueReader.stringValue(document.fileName).lowercase()
        return DocumentConstants.IMAGE_EXTENSIONS.any { fileName.endsWith(it) }
    }

    companion object {
        fun from(message: TelegramMessage): MessageExtractor = MessageExtractor(message)
    }
}
