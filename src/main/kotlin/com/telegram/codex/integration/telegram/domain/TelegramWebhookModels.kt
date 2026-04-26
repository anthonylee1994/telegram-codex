package com.telegram.codex.integration.telegram.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramUser(
    val id: Long?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramUpdate(
    @param:JsonProperty("update_id") val updateId: Long?,
    val message: TelegramMessage?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramMessage(
    @param:JsonProperty("message_id") val messageId: Long?,
    val chat: TelegramChat?,
    val from: TelegramUser?,
    val text: String?,
    val caption: String?,
    @param:JsonProperty("media_group_id") val mediaGroupId: String?,
    val photo: List<TelegramPhotoSize>?,
    val document: TelegramDocument?,
    @param:JsonProperty("reply_to_message") val replyToMessage: TelegramMessage?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramChat(
    val id: Long?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramDocument(
    @param:JsonProperty("file_id") val fileId: String?,
    @param:JsonProperty("mime_type") val mimeType: String?,
    @param:JsonProperty("file_name") val fileName: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TelegramPhotoSize(
    @param:JsonProperty("file_id") val fileId: String?,
    @param:JsonProperty("file_size") val fileSize: Long?,
)
