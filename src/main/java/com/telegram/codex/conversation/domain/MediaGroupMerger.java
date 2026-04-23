package com.telegram.codex.conversation.domain;

import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class MediaGroupMerger {

    public InboundMessage merge(List<InboundMessage> messages) {
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Cannot merge empty message list");
        }

        List<InboundMessage> sorted = messages.stream()
            .sorted(Comparator.comparingLong(InboundMessage::messageId).thenComparingLong(InboundMessage::updateId))
            .toList();

        InboundMessage primary = sorted.getFirst();

        String aggregatedText = sorted.stream()
            .map(InboundMessage::text)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(null);

        List<String> aggregatedImageFileIds = sorted.stream()
            .flatMap(message -> message.imageFileIds().stream())
            .distinct()
            .toList();

        List<InboundMessage.ProcessingUpdate> processingUpdates = sorted.stream()
            .map(message -> new InboundMessage.ProcessingUpdate(message.updateId(), message.messageId()))
            .toList();

        return InboundMessage.builder()
            .chatId(primary.chatId())
            .imageFileIds(aggregatedImageFileIds)
            .mediaGroupId(primary.mediaGroupId())
            .messageId(primary.messageId())
            .processingUpdates(processingUpdates)
            .text(aggregatedText)
            .userId(primary.userId())
            .updateId(primary.updateId())
            .build();
    }
}
