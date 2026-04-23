package com.telegram.codex.integration.telegram.domain;

import com.telegram.codex.conversation.domain.MessageConstants;
import com.telegram.codex.integration.telegram.domain.document.DocumentTypeRegistry;

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
    private final DocumentTypeRegistry documentTypeRegistry;

    public MessageExtractor(Map<String, Object> message, DocumentTypeRegistry documentTypeRegistry) {
        this.message = message;
        this.documentTypeRegistry = documentTypeRegistry;
    }

    public static MessageExtractor from(Map<String, Object> message, DocumentTypeRegistry documentTypeRegistry) {
        return new MessageExtractor(message, documentTypeRegistry);
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
        String text = TelegramPayloadValueReader.stringValue(message.get("text"));
        if (!text.isBlank()) {
            return TelegramPayloadValueReader.blankToNull(text);
        }
        return TelegramPayloadValueReader.blankToNull(TelegramPayloadValueReader.stringValue(message.get("caption")));
    }

    public List<String> getImageFileIds() {
        return getDocument()
            .filter(documentTypeRegistry::isImageDocument)
            .map(doc -> List.of(TelegramPayloadValueReader.stringValue(doc.get("file_id"))))
            .orElseGet(() -> getPhotoFileId().map(List::of).orElse(List.of()));
    }

    public Optional<MessageExtractor> getReplyToMessage() {
        return Optional.ofNullable(TelegramPayloadValueReader.castMap(message.get("reply_to_message")))
            .map(reply -> new MessageExtractor(reply, documentTypeRegistry));
    }

    public Optional<String> getReplyToText() {
        return getReplyToMessage().flatMap(reply -> {
            Optional<String> text = Optional.ofNullable(reply.getText());
            if (text.isPresent()) {
                return text;
            }
            if (reply.hasPhoto()) {
                return Optional.of(MessageConstants.REPLY_TO_IMAGE);
            }
            if (reply.getDocument().filter(documentTypeRegistry::isImageDocument).isPresent()) {
                return Optional.of(MessageConstants.REPLY_TO_IMAGE_DOCUMENT);
            }
            return Optional.empty();
        });
    }

    public boolean hasPhoto() {
        return !TelegramPayloadValueReader.castListOfMaps(message.get("photo")).isEmpty();
    }

    public boolean isSupported() {
        return message.get("text") instanceof String
            || hasPhoto()
            || getDocument().filter(documentTypeRegistry::isImageDocument).isPresent();
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

    private Optional<String> getPhotoFileId() {
        List<Map<String, Object>> photos = TelegramPayloadValueReader.castListOfMaps(message.get("photo"));
        return photos.stream()
            .max(Comparator.comparingLong(photo -> {
                Long fileSize = TelegramPayloadValueReader.longObjectValue(photo.get("file_size"));
                return fileSize != null ? fileSize : 0L;
            }))
            .map(photo -> TelegramPayloadValueReader.stringValue(photo.get("file_id")));
    }
}
