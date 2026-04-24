package com.telegram.codex.integration.telegram.domain.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramDocument(
    @JsonProperty("file_id") String fileId,
    @JsonProperty("mime_type") String mimeType,
    @JsonProperty("file_name") String fileName
) {
}
