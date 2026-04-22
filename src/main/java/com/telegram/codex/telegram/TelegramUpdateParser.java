package com.telegram.codex.telegram;

import com.telegram.codex.constants.MessageConstants;
import com.telegram.codex.telegram.document.DocumentTypeRegistry;
import com.telegram.codex.util.MapUtils;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class TelegramUpdateParser {

    private final DocumentTypeRegistry documentTypeRegistry;

    public TelegramUpdateParser(DocumentTypeRegistry documentTypeRegistry) {
        this.documentTypeRegistry = documentTypeRegistry;
    }

    public InboundMessage parseIncomingTelegramMessage(Map<String, Object> update) {
        if (!supportedMessage(update)) {
            return null;
        }

        Map<String, Object> message = MapUtils.castMap(update.get("message"));
        return new InboundMessage(
            MapUtils.stringValue(MapUtils.castMap(message.get("chat")).get("id")),
            buildImageFileIds(message),
            MapUtils.blankToNull(MapUtils.stringValue(message.get("media_group_id"))),
            MapUtils.longValue(message.get("message_id")),
            buildPdfFileId(message),
            List.of(),
            buildReplyToImageFileIds(message),
            buildReplyToMessageId(message),
            buildReplyToPdfFileId(message),
            buildReplyToText(message),
            buildReplyToTextDocumentFileId(message),
            buildReplyToTextDocumentName(message),
            MapUtils.blankToNull(MapUtils.stringValue(message.get("text")).isBlank() ? MapUtils.stringValue(message.get("caption")) : MapUtils.stringValue(message.get("text"))),
            buildTextDocumentFileId(message),
            buildTextDocumentName(message),
            MapUtils.stringValue(MapUtils.castMap(message.get("from")).get("id")),
            MapUtils.longValue(update.get("update_id"))
        );
    }

    private boolean supportedMessage(Map<String, Object> update) {
        if (update == null || !(update.get("update_id") instanceof Number)) {
            return false;
        }
        Map<String, Object> message = MapUtils.castMap(update.get("message"));
        if (message == null || !(message.get("message_id") instanceof Number)) {
            return false;
        }
        if (MapUtils.castMap(message.get("from")) == null || MapUtils.castMap(message.get("chat")) == null) {
            return false;
        }
        return message.get("text") instanceof String
            || hasPhoto(message)
            || documentTypeRegistry.isImageDocument(MapUtils.castMap(message.get("document")))
            || documentTypeRegistry.isPdfDocument(MapUtils.castMap(message.get("document")))
            || documentTypeRegistry.isTextDocument(MapUtils.castMap(message.get("document")));
    }

    private List<String> buildImageFileIds(Map<String, Object> message) {
        if (message == null) {
            return List.of();
        }
        Map<String, Object> document = MapUtils.castMap(message.get("document"));
        if (documentTypeRegistry.isImageDocument(document)) {
            return List.of(MapUtils.stringValue(document.get("file_id")));
        }
        List<Map<String, Object>> photos = MapUtils.castListOfMaps(message.get("photo"));
        return photos.stream()
            .max(Comparator.comparingLong(photo -> MapUtils.longObjectValue(photo.get("file_size")) == null ? 0L : MapUtils.longObjectValue(photo.get("file_size"))))
            .map(photo -> List.of(MapUtils.stringValue(photo.get("file_id"))))
            .orElse(List.of());
    }

    private String buildPdfFileId(Map<String, Object> message) {
        if (message == null) {
            return null;
        }
        Map<String, Object> document = MapUtils.castMap(message.get("document"));
        return documentTypeRegistry.isPdfDocument(document) ? MapUtils.stringValue(document.get("file_id")) : null;
    }

    private String buildTextDocumentFileId(Map<String, Object> message) {
        if (message == null) {
            return null;
        }
        Map<String, Object> document = MapUtils.castMap(message.get("document"));
        return documentTypeRegistry.isTextDocument(document) ? MapUtils.stringValue(document.get("file_id")) : null;
    }

    private String buildTextDocumentName(Map<String, Object> message) {
        if (message == null) {
            return null;
        }
        Map<String, Object> document = MapUtils.castMap(message.get("document"));
        return documentTypeRegistry.isTextDocument(document) ? MapUtils.blankToNull(MapUtils.stringValue(document.get("file_name"))) : null;
    }

    private List<String> buildReplyToImageFileIds(Map<String, Object> message) {
        if (message == null) {
            return List.of();
        }
        return buildImageFileIds(MapUtils.castMap(message.get("reply_to_message")));
    }

    private Long buildReplyToMessageId(Map<String, Object> message) {
        if (message == null) {
            return null;
        }
        Map<String, Object> reply = MapUtils.castMap(message.get("reply_to_message"));
        return reply == null ? null : MapUtils.longObjectValue(reply.get("message_id"));
    }

    private String buildReplyToPdfFileId(Map<String, Object> message) {
        if (message == null) {
            return null;
        }
        return buildPdfFileId(MapUtils.castMap(message.get("reply_to_message")));
    }

    private String buildReplyToText(Map<String, Object> message) {
        if (message == null) {
            return null;
        }
        Map<String, Object> reply = MapUtils.castMap(message.get("reply_to_message"));
        if (reply == null) {
            return null;
        }
        String text = MapUtils.blankToNull(MapUtils.stringValue(reply.get("text")).isBlank() ? MapUtils.stringValue(reply.get("caption")) : MapUtils.stringValue(reply.get("text")));
        if (text != null) {
            return text;
        }
        if (hasPhoto(reply)) {
            return MessageConstants.REPLY_TO_IMAGE;
        }
        if (documentTypeRegistry.isImageDocument(MapUtils.castMap(reply.get("document")))) {
            return MessageConstants.REPLY_TO_IMAGE_DOCUMENT;
        }
        if (documentTypeRegistry.isPdfDocument(MapUtils.castMap(reply.get("document")))) {
            return MessageConstants.REPLY_TO_PDF;
        }
        if (documentTypeRegistry.isTextDocument(MapUtils.castMap(reply.get("document")))) {
            return MessageConstants.REPLY_TO_TEXT_DOCUMENT;
        }
        return null;
    }

    private String buildReplyToTextDocumentFileId(Map<String, Object> message) {
        if (message == null) {
            return null;
        }
        return buildTextDocumentFileId(MapUtils.castMap(message.get("reply_to_message")));
    }

    private String buildReplyToTextDocumentName(Map<String, Object> message) {
        if (message == null) {
            return null;
        }
        return buildTextDocumentName(MapUtils.castMap(message.get("reply_to_message")));
    }

    private boolean hasPhoto(Map<String, Object> message) {
        if (message == null) {
            return false;
        }
        List<Map<String, Object>> photos = MapUtils.castListOfMaps(message.get("photo"));
        return !photos.isEmpty();
    }
}
