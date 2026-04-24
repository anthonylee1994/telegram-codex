package com.telegram.codex.integration.telegram.domain;

import com.telegram.codex.conversation.domain.MessageConstants;
import com.telegram.codex.integration.telegram.domain.document.DocumentConstants;
import com.telegram.codex.integration.telegram.domain.webhook.TelegramChat;
import com.telegram.codex.integration.telegram.domain.webhook.TelegramDocument;
import com.telegram.codex.integration.telegram.domain.webhook.TelegramMessage;
import com.telegram.codex.integration.telegram.domain.webhook.TelegramPhotoSize;
import com.telegram.codex.integration.telegram.domain.webhook.TelegramUser;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Helper class to extract data from Telegram message maps.
 * Encapsulates null-safe extraction logic and reduces repetition.
 */
public class MessageExtractor {

    private final TelegramMessage message;

    public MessageExtractor(TelegramMessage message) {
        this.message = message;
    }

    public static MessageExtractor from(TelegramMessage message) {
        return new MessageExtractor(message);
    }

    public String getChatId() {
        return TelegramPayloadValueReader.stringValue(getChat().map(TelegramChat::id).orElse(null));
    }

    public long getMessageId() {
        return message.messageId();
    }

    public String getUserId() {
        return TelegramPayloadValueReader.stringValue(getFrom().map(TelegramUser::id).orElse(null));
    }

    public String getMediaGroupId() {
        return TelegramPayloadValueReader.blankToNull(TelegramPayloadValueReader.stringValue(message.mediaGroupId()));
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
        return Optional.ofNullable(message.replyToMessage())
            .map(MessageExtractor::new);
    }

    public Optional<String> getReplyToText() {
        return getReplyToMessage().flatMap(this::resolveReplyToText);
    }

    public boolean hasPhoto() {
        return !getPhotos().isEmpty();
    }

    public boolean isSupported() {
        return message.text() != null
            || hasPhoto()
            || imageDocumentFileId().isPresent();
    }

    private Optional<TelegramChat> getChat() {
        return Optional.ofNullable(message.chat());
    }

    private Optional<TelegramUser> getFrom() {
        return Optional.ofNullable(message.from());
    }

    private Optional<TelegramDocument> getDocument() {
        return Optional.ofNullable(message.document());
    }

    private List<TelegramPhotoSize> getPhotos() {
        return message.photo() == null ? List.of() : message.photo();
    }

    private String textValue(String key) {
        return switch (key) {
            case "text" -> TelegramPayloadValueReader.stringValue(message.text());
            case "caption" -> TelegramPayloadValueReader.stringValue(message.caption());
            default -> "";
        };
    }

    private Optional<String> imageDocumentFileId() {
        return getDocument()
            .filter(this::isImageDocument)
            .map(document -> TelegramPayloadValueReader.stringValue(document.fileId()));
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
        return getPhotos().stream()
            .max(Comparator.comparingLong(photo -> {
                Long fileSize = photo.fileSize();
                return fileSize != null ? fileSize : 0L;
            }))
            .map(photo -> TelegramPayloadValueReader.stringValue(photo.fileId()));
    }

    private boolean isImageDocument(TelegramDocument document) {
        if (document == null || document.fileId() == null) {
            return false;
        }
        String mimeType = TelegramPayloadValueReader.stringValue(document.mimeType()).toLowerCase();
        if (DocumentConstants.IMAGE_MIME_TYPE_PREFIXES.stream().anyMatch(mimeType::startsWith)) {
            return true;
        }
        String fileName = TelegramPayloadValueReader.stringValue(document.fileName()).toLowerCase();
        return DocumentConstants.IMAGE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
}
