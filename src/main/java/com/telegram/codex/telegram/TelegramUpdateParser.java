package com.telegram.codex.telegram;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class TelegramUpdateParser {

    public InboundMessage parseIncomingTelegramMessage(Map<String, Object> update) {
        if (!supportedMessage(update)) {
            return null;
        }

        Map<String, Object> message = castMap(update.get("message"));
        return new InboundMessage(
            stringValue(castMap(message.get("chat")).get("id")),
            buildImageFileIds(message),
            blankToNull(stringValue(message.get("media_group_id"))),
            longValue(message.get("message_id")),
            buildPdfFileId(message),
            List.of(),
            buildReplyToImageFileIds(message),
            buildReplyToMessageId(message),
            buildReplyToPdfFileId(message),
            buildReplyToText(message),
            buildReplyToTextDocumentFileId(message),
            buildReplyToTextDocumentName(message),
            blankToNull(stringValue(message.get("text")).isBlank() ? stringValue(message.get("caption")) : stringValue(message.get("text"))),
            buildTextDocumentFileId(message),
            buildTextDocumentName(message),
            stringValue(castMap(message.get("from")).get("id")),
            longValue(update.get("update_id"))
        );
    }

    private boolean supportedMessage(Map<String, Object> update) {
        if (update == null || !(update.get("update_id") instanceof Number)) {
            return false;
        }
        Map<String, Object> message = castMap(update.get("message"));
        if (message == null || !(message.get("message_id") instanceof Number)) {
            return false;
        }
        if (castMap(message.get("from")) == null || castMap(message.get("chat")) == null) {
            return false;
        }
        return message.get("text") instanceof String
            || hasPhoto(message)
            || supportedDocumentImage(castMap(message.get("document")))
            || supportedPdfDocument(castMap(message.get("document")))
            || supportedTextDocument(castMap(message.get("document")));
    }

    private List<String> buildImageFileIds(Map<String, Object> message) {
        if (message == null) {
            return List.of();
        }
        Map<String, Object> document = castMap(message.get("document"));
        if (supportedDocumentImage(document)) {
            return List.of(stringValue(document.get("file_id")));
        }
        List<Map<String, Object>> photos = castListOfMaps(message.get("photo"));
        return photos.stream()
            .max(Comparator.comparingLong(photo -> longObjectValue(photo.get("file_size")) == null ? 0L : longObjectValue(photo.get("file_size"))))
            .map(photo -> List.of(stringValue(photo.get("file_id"))))
            .orElse(List.of());
    }

    private String buildPdfFileId(Map<String, Object> message) {
        if (message == null) {
            return null;
        }
        Map<String, Object> document = castMap(message.get("document"));
        return supportedPdfDocument(document) ? stringValue(document.get("file_id")) : null;
    }

    private String buildTextDocumentFileId(Map<String, Object> message) {
        if (message == null) {
            return null;
        }
        Map<String, Object> document = castMap(message.get("document"));
        return supportedTextDocument(document) ? stringValue(document.get("file_id")) : null;
    }

    private String buildTextDocumentName(Map<String, Object> message) {
        if (message == null) {
            return null;
        }
        Map<String, Object> document = castMap(message.get("document"));
        return supportedTextDocument(document) ? blankToNull(stringValue(document.get("file_name"))) : null;
    }

    private List<String> buildReplyToImageFileIds(Map<String, Object> message) {
        if (message == null) {
            return List.of();
        }
        return buildImageFileIds(castMap(message.get("reply_to_message")));
    }

    private Long buildReplyToMessageId(Map<String, Object> message) {
        if (message == null) {
            return null;
        }
        Map<String, Object> reply = castMap(message.get("reply_to_message"));
        return reply == null ? null : longObjectValue(reply.get("message_id"));
    }

    private String buildReplyToPdfFileId(Map<String, Object> message) {
        if (message == null) {
            return null;
        }
        return buildPdfFileId(castMap(message.get("reply_to_message")));
    }

    private String buildReplyToText(Map<String, Object> message) {
        if (message == null) {
            return null;
        }
        Map<String, Object> reply = castMap(message.get("reply_to_message"));
        if (reply == null) {
            return null;
        }
        String text = blankToNull(stringValue(reply.get("text")).isBlank() ? stringValue(reply.get("caption")) : stringValue(reply.get("text")));
        if (text != null) {
            return text;
        }
        if (hasPhoto(reply)) {
            return "用戶引用咗一張相。";
        }
        if (supportedDocumentImage(castMap(reply.get("document")))) {
            return "用戶引用咗一個圖片檔案。";
        }
        if (supportedPdfDocument(castMap(reply.get("document")))) {
            return "用戶引用咗一份 PDF。";
        }
        if (supportedTextDocument(castMap(reply.get("document")))) {
            return "用戶引用咗一份文字檔。";
        }
        return null;
    }

    private String buildReplyToTextDocumentFileId(Map<String, Object> message) {
        if (message == null) {
            return null;
        }
        return buildTextDocumentFileId(castMap(message.get("reply_to_message")));
    }

    private String buildReplyToTextDocumentName(Map<String, Object> message) {
        if (message == null) {
            return null;
        }
        return buildTextDocumentName(castMap(message.get("reply_to_message")));
    }

    private boolean supportedDocumentImage(Map<String, Object> document) {
        if (document == null || !(document.get("file_id") instanceof String)) {
            return false;
        }
        String mimeType = stringValue(document.get("mime_type")).toLowerCase();
        if (mimeType.startsWith("image/")) {
            return true;
        }
        String fileName = stringValue(document.get("file_name")).toLowerCase();
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png") || fileName.endsWith(".webp");
    }

    private boolean supportedPdfDocument(Map<String, Object> document) {
        if (document == null || !(document.get("file_id") instanceof String)) {
            return false;
        }
        String mimeType = stringValue(document.get("mime_type")).toLowerCase();
        if ("application/pdf".equals(mimeType)) {
            return true;
        }
        return stringValue(document.get("file_name")).toLowerCase().endsWith(".pdf");
    }

    private boolean supportedTextDocument(Map<String, Object> document) {
        if (document == null || !(document.get("file_id") instanceof String)) {
            return false;
        }
        if (supportedDocumentImage(document) || supportedPdfDocument(document)) {
            return false;
        }
        String mimeType = stringValue(document.get("mime_type")).toLowerCase();
        if (List.of(
            "text/plain",
            "text/markdown",
            "text/html",
            "application/xhtml+xml",
            "application/json",
            "text/csv",
            "application/csv",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ).contains(mimeType)) {
            return true;
        }
        String fileName = stringValue(document.get("file_name")).toLowerCase();
        return fileName.endsWith(".txt")
            || fileName.endsWith(".md")
            || fileName.endsWith(".html")
            || fileName.endsWith(".json")
            || fileName.endsWith(".csv")
            || fileName.endsWith(".docx")
            || fileName.endsWith(".xlsx");
    }

    private boolean hasPhoto(Map<String, Object> message) {
        if (message == null) {
            return false;
        }
        List<Map<String, Object>> photos = castListOfMaps(message.get("photo"));
        return !photos.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castListOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
            .filter(item -> item instanceof Map<?, ?>)
            .map(item -> (Map<String, Object>) item)
            .toList();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long longValue(Object value) {
        return ((Number) value).longValue();
    }

    private Long longObjectValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
