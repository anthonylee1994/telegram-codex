package com.telegram.codex.integration.telegram.domain;

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
    @JsonProperty("processing_updates") List<ProcessingUpdate> processingUpdates,
    @JsonProperty("reply_to_image_file_ids") List<String> replyToImageFileIds,
    @JsonProperty("reply_to_message_id") Long replyToMessageId,
    @JsonProperty("reply_to_text") String replyToText,
    @JsonProperty("text") String text,
    @JsonProperty("user_id") String userId,
    @JsonProperty("update_id") long updateId
) {

    public InboundMessage {
        imageFileIds = normalizeStrings(imageFileIds);
        replyToImageFileIds = normalizeStrings(replyToImageFileIds);
        mediaGroupId = normalizeNullableString(mediaGroupId);
        replyToText = normalizeNullableString(replyToText);
        text = normalizeNullableString(text);
        processingUpdates = normalizeProcessingUpdates(processingUpdates, updateId, messageId);
    }

    public boolean mediaGroup() {
        return mediaGroupId != null;
    }

    public boolean unsupported() {
        return (text == null || text.isBlank())
            && imageFileIds.isEmpty();
    }

    public int imageCount() {
        return imageFileIds.size();
    }

    public String textOrEmpty() {
        return text == null ? "" : text;
    }

    public List<String> effectiveImageFileIds() {
        return imageFileIds.isEmpty() ? replyToImageFileIds : imageFileIds;
    }

    public record ProcessingUpdate(@JsonProperty("update_id") long updateId, @JsonProperty("message_id") long messageId) {
    }

    public static InboundMessage forMergedMediaGroup(
        InboundMessage primary,
        List<String> imageFileIds,
        List<ProcessingUpdate> processingUpdates,
        String text
    ) {
        return new InboundMessage(
            primary.chatId(),
            imageFileIds,
            primary.mediaGroupId(),
            primary.messageId(),
            processingUpdates,
            List.of(),
            null,
            null,
            text,
            primary.userId(),
            primary.updateId()
        );
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
        return TelegramPayloadValueReader.blankToNull(value);
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
