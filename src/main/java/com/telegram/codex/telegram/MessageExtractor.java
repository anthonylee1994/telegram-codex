package com.telegram.codex.telegram;

import com.telegram.codex.constants.MessageConstants;
import com.telegram.codex.telegram.document.DocumentTypeRegistry;
import com.telegram.codex.util.MapUtils;

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
        return MapUtils.stringValue(getChat().map(chat -> chat.get("id")).orElse(null));
    }

    public long getMessageId() {
        return MapUtils.longValue(message.get("message_id"));
    }

    public String getUserId() {
        return MapUtils.stringValue(getFrom().map(from -> from.get("id")).orElse(null));
    }

    public String getMediaGroupId() {
        return MapUtils.blankToNull(MapUtils.stringValue(message.get("media_group_id")));
    }

    public String getText() {
        String text = MapUtils.stringValue(message.get("text"));
        if (!text.isBlank()) {
            return MapUtils.blankToNull(text);
        }
        return MapUtils.blankToNull(MapUtils.stringValue(message.get("caption")));
    }

    public List<String> getImageFileIds() {
        return getDocument()
            .filter(documentTypeRegistry::isImageDocument)
            .map(doc -> List.of(MapUtils.stringValue(doc.get("file_id"))))
            .orElseGet(() -> getPhotoFileId().map(List::of).orElse(List.of()));
    }

    public Optional<String> getPdfFileId() {
        return getDocument()
            .filter(documentTypeRegistry::isPdfDocument)
            .map(doc -> MapUtils.stringValue(doc.get("file_id")));
    }

    public Optional<String> getTextDocumentFileId() {
        return getDocument()
            .filter(documentTypeRegistry::isTextDocument)
            .map(doc -> MapUtils.stringValue(doc.get("file_id")));
    }

    public Optional<String> getTextDocumentName() {
        return getDocument()
            .filter(documentTypeRegistry::isTextDocument)
            .map(doc -> MapUtils.blankToNull(MapUtils.stringValue(doc.get("file_name"))));
    }

    public Optional<MessageExtractor> getReplyToMessage() {
        return Optional.ofNullable(MapUtils.castMap(message.get("reply_to_message")))
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
            if (reply.getDocument().filter(documentTypeRegistry::isPdfDocument).isPresent()) {
                return Optional.of(MessageConstants.REPLY_TO_PDF);
            }
            if (reply.getDocument().filter(documentTypeRegistry::isTextDocument).isPresent()) {
                return Optional.of(MessageConstants.REPLY_TO_TEXT_DOCUMENT);
            }
            return Optional.empty();
        });
    }

    public boolean hasPhoto() {
        return !MapUtils.castListOfMaps(message.get("photo")).isEmpty();
    }

    public boolean isSupported() {
        return message.get("text") instanceof String
            || hasPhoto()
            || getDocument().filter(documentTypeRegistry::isImageDocument).isPresent()
            || getDocument().filter(documentTypeRegistry::isPdfDocument).isPresent()
            || getDocument().filter(documentTypeRegistry::isTextDocument).isPresent();
    }

    private Optional<Map<String, Object>> getChat() {
        return Optional.ofNullable(MapUtils.castMap(message.get("chat")));
    }

    private Optional<Map<String, Object>> getFrom() {
        return Optional.ofNullable(MapUtils.castMap(message.get("from")));
    }

    private Optional<Map<String, Object>> getDocument() {
        return Optional.ofNullable(MapUtils.castMap(message.get("document")));
    }

    private Optional<String> getPhotoFileId() {
        List<Map<String, Object>> photos = MapUtils.castListOfMaps(message.get("photo"));
        return photos.stream()
            .max(Comparator.comparingLong(photo -> {
                Long fileSize = MapUtils.longObjectValue(photo.get("file_size"));
                return fileSize != null ? fileSize : 0L;
            }))
            .map(photo -> MapUtils.stringValue(photo.get("file_id")));
    }
}
