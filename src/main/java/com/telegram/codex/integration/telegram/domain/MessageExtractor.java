package com.telegram.codex.integration.telegram.domain;

import com.telegram.codex.conversation.domain.MessageConstants;
import com.telegram.codex.integration.telegram.domain.document.DocumentConstants;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Helper class to extract data from Telegram message maps.
 * Encapsulates null-safe extraction logic and reduces repetition.
 */
public class MessageExtractor {

    private final Map<String, Object> message;

    public MessageExtractor(Map<String, Object> message) {
        this.message = message;
    }

    public static MessageExtractor from(Map<String, Object> message) {
        return new MessageExtractor(message);
    }

    public String getChatId() {
        return TelegramPayloadValueReader.stringValue(getChat().map(chat -> chat.get("id")).orElse(null));
    }

    public long getMessageId() {
        return TelegramPayloadValueReader.longValue(message.get("message_id"));
    }

    public String getUserId() {
        return TelegramPayloadValueReader.stringValue(getFrom().map(from -> from.get("id")).orElse(null));
    }

    public String getMediaGroupId() {
        return TelegramPayloadValueReader.blankToNull(TelegramPayloadValueReader.stringValue(message.get("media_group_id")));
    }

    public String getText() {
        String text = textValue("text");
        if (!text.isBlank()) {
            return TelegramPayloadValueReader.blankToNull(text);
        }
        return TelegramPayloadValueReader.blankToNull(textValue("caption"));
    }

    public List<String> getImageFileIds() {
        return imageDocumentFileId()
            .map(List::of)
            .orElseGet(() -> getPhotoFileId().map(List::of).orElse(List.of()));
    }

    public Optional<MessageExtractor> getReplyToMessage() {
        return Optional.ofNullable(TelegramPayloadValueReader.castMap(message.get("reply_to_message")))
            .map(MessageExtractor::new);
    }

    public Optional<String> getReplyToText() {
        return getReplyToMessage().flatMap(this::resolveReplyToText);
    }

    public boolean hasPhoto() {
        return !TelegramPayloadValueReader.castListOfMaps(message.get("photo")).isEmpty();
    }

    public boolean isSupported() {
        return message.get("text") instanceof String
            || hasPhoto()
            || imageDocumentFileId().isPresent();
    }

    private Optional<Map<String, Object>> getChat() {
        return Optional.ofNullable(TelegramPayloadValueReader.castMap(message.get("chat")));
    }

    private Optional<Map<String, Object>> getFrom() {
        return Optional.ofNullable(TelegramPayloadValueReader.castMap(message.get("from")));
    }

    private Optional<Map<String, Object>> getDocument() {
        return Optional.ofNullable(TelegramPayloadValueReader.castMap(message.get("document")));
    }

    private String textValue(String key) {
        return TelegramPayloadValueReader.stringValue(message.get(key));
    }

    private Optional<String> imageDocumentFileId() {
        return getDocument()
            .filter(this::isImageDocument)
            .map(document -> TelegramPayloadValueReader.stringValue(document.get("file_id")));
    }

    private Optional<String> resolveReplyToText(MessageExtractor reply) {
        Optional<String> text = Optional.ofNullable(reply.getText());
        if (text.isPresent()) {
            return text;
        }
        if (reply.hasPhoto()) {
            return Optional.of(MessageConstants.REPLY_TO_IMAGE);
        }
        if (reply.imageDocumentFileId().isPresent()) {
            return Optional.of(MessageConstants.REPLY_TO_IMAGE_DOCUMENT);
        }
        return Optional.empty();
    }

    private Optional<String> getPhotoFileId() {
        List<Map<String, Object>> photos = TelegramPayloadValueReader.castListOfMaps(message.get("photo"));
        return photos.stream()
            .max(Comparator.comparingLong(photo -> {
                Long fileSize = TelegramPayloadValueReader.longObjectValue(photo.get("file_size"));
                return fileSize != null ? fileSize : 0L;
            }))
            .map(photo -> TelegramPayloadValueReader.stringValue(photo.get("file_id")));
    }

    private boolean isImageDocument(Map<String, Object> document) {
        if (document == null || document.get("file_id") == null) {
            return false;
        }
        String mimeType = TelegramPayloadValueReader.stringValue(document.get("mime_type")).toLowerCase();
        if (DocumentConstants.IMAGE_MIME_TYPE_PREFIXES.stream().anyMatch(mimeType::startsWith)) {
            return true;
        }
        String fileName = TelegramPayloadValueReader.stringValue(document.get("file_name")).toLowerCase();
        return DocumentConstants.IMAGE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
}
