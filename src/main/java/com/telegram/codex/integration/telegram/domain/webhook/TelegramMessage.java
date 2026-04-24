package com.telegram.codex.integration.telegram.domain.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramMessage(
    @JsonProperty("message_id") Long messageId,
    TelegramChat chat,
    TelegramUser from,
    String text,
    String caption,
    @JsonProperty("media_group_id") String mediaGroupId,
    List<TelegramPhotoSize> photo,
    TelegramDocument document,
    @JsonProperty("reply_to_message") TelegramMessage replyToMessage
) {
}
