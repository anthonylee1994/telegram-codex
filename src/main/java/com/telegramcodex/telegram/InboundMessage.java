package com.telegramcodex.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public record InboundMessage(
    @JsonProperty("chat_id") String chatId,
    @JsonProperty("image_file_ids") List<String> imageFileIds,
    @JsonProperty("media_group_id") String mediaGroupId,
    @JsonProperty("message_id") long messageId,
    @JsonProperty("pdf_file_id") String pdfFileId,
    @JsonProperty("processing_updates") List<ProcessingUpdate> processingUpdates,
    @JsonProperty("reply_to_image_file_ids") List<String> replyToImageFileIds,
    @JsonProperty("reply_to_message_id") Long replyToMessageId,
    @JsonProperty("reply_to_pdf_file_id") String replyToPdfFileId,
    @JsonProperty("reply_to_text") String replyToText,
    @JsonProperty("reply_to_text_document_file_id") String replyToTextDocumentFileId,
    @JsonProperty("reply_to_text_document_name") String replyToTextDocumentName,
    @JsonProperty("text") String text,
    @JsonProperty("text_document_file_id") String textDocumentFileId,
    @JsonProperty("text_document_name") String textDocumentName,
    @JsonProperty("user_id") String userId,
    @JsonProperty("update_id") long updateId
) {

    public InboundMessage {
        imageFileIds = normalizeStrings(imageFileIds);
        replyToImageFileIds = normalizeStrings(replyToImageFileIds);
        mediaGroupId = normalizeNullableString(mediaGroupId);
        pdfFileId = normalizeNullableString(pdfFileId);
        replyToPdfFileId = normalizeNullableString(replyToPdfFileId);
        replyToText = normalizeNullableString(replyToText);
        replyToTextDocumentFileId = normalizeNullableString(replyToTextDocumentFileId);
        replyToTextDocumentName = normalizeNullableString(replyToTextDocumentName);
        text = normalizeNullableString(text);
        textDocumentFileId = normalizeNullableString(textDocumentFileId);
        textDocumentName = normalizeNullableString(textDocumentName);
        processingUpdates = normalizeProcessingUpdates(processingUpdates, updateId, messageId);
    }

    public boolean mediaGroup() {
        return mediaGroupId != null;
    }

    public boolean unsupported() {
        return (text == null || text.isBlank())
            && imageFileIds.isEmpty()
            && pdfFileId == null
            && textDocumentFileId == null;
    }

    public int imageCount() {
        return imageFileIds.size();
    }

    public List<String> effectiveImageFileIds() {
        return imageFileIds.isEmpty() ? replyToImageFileIds : imageFileIds;
    }

    public String effectivePdfFileId() {
        if (pdfFileId != null) {
            return pdfFileId;
        }
        if (!imageFileIds.isEmpty() || textDocumentFileId != null) {
            return null;
        }
        return replyToPdfFileId;
    }

    public String effectiveTextDocumentFileId() {
        if (textDocumentFileId != null) {
            return textDocumentFileId;
        }
        if (!imageFileIds.isEmpty() || pdfFileId != null) {
            return null;
        }
        return replyToTextDocumentFileId;
    }

    public String effectiveTextDocumentName() {
        if (textDocumentName != null) {
            return textDocumentName;
        }
        if (!imageFileIds.isEmpty() || pdfFileId != null) {
            return null;
        }
        return replyToTextDocumentName;
    }

    public boolean replyingToFile() {
        return !replyToImageFileIds.isEmpty() || replyToPdfFileId != null || replyToTextDocumentFileId != null;
    }

    public record ProcessingUpdate(@JsonProperty("update_id") long updateId, @JsonProperty("message_id") long messageId) {
    }

    public static List<String> normalizeStrings(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = normalizeNullableString(value);
            if (normalized != null) {
                seen.add(normalized);
            }
        }
        return List.copyOf(seen);
    }

    private static String normalizeNullableString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static List<ProcessingUpdate> normalizeProcessingUpdates(
        List<ProcessingUpdate> updates,
        long updateId,
        long messageId
    ) {
        List<ProcessingUpdate> source = updates == null || updates.isEmpty()
            ? List.of(new ProcessingUpdate(updateId, messageId))
            : updates;
        ArrayList<ProcessingUpdate> normalized = new ArrayList<>();
        for (ProcessingUpdate update : source) {
            if (update == null) {
                continue;
            }
            normalized.add(update);
        }
        normalized.sort(Comparator.comparingLong(ProcessingUpdate::messageId).thenComparingLong(ProcessingUpdate::updateId));
        return List.copyOf(normalized);
    }
}
