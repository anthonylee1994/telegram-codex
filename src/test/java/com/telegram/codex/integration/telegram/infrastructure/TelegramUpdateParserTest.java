package com.telegram.codex.integration.telegram.infrastructure;

import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TelegramUpdateParserTest {

    private final TelegramUpdateParser parser = new TelegramUpdateParser();

    @Test
    void parsesPhotoMessageAndReplyContext() {
        Map<String, Object> update = Map.of(
            "update_id", 99,
            "message", Map.of(
                "message_id", 12,
                "chat", Map.of("id", 3),
                "from", Map.of("id", 5),
                "caption", "睇下呢張圖",
                "photo", List.of(
                    Map.of("file_id", "small", "file_size", 100),
                    Map.of("file_id", "large", "file_size", 200)
                ),
                "reply_to_message", Map.of(
                    "message_id", 10,
                    "document", Map.of("file_id", "doc-1", "mime_type", "application/pdf", "file_name", "a.pdf")
                )
            )
        );

        InboundMessage message = parser.parseIncomingTelegramMessage(update);

        assertNotNull(message);
        assertEquals("3", message.chatId());
        assertEquals(List.of("large"), message.imageFileIds());
        assertNull(message.replyToText());
    }

    @Test
    void ignoresUnsupportedMessages() {
        Map<String, Object> update = Map.of(
            "update_id", 99,
            "message", Map.of(
                "message_id", 12,
                "chat", Map.of("id", 3),
                "from", Map.of("id", 5),
                "sticker", Map.of("file_id", "sticker-1")
            )
        );

        assertNull(parser.parseIncomingTelegramMessage(update));
    }

    @Test
    void ignoresUnsupportedDocuments() {
        Map<String, Object> update = Map.of(
            "update_id", 101,
            "message", Map.of(
                "message_id", 14,
                "chat", Map.of("id", 3),
                "from", Map.of("id", 5),
                "document", Map.of(
                    "file_id", "doc-2",
                    "mime_type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "file_name", "brief.docx"
                )
            )
        );

        assertNull(parser.parseIncomingTelegramMessage(update));
    }

    @Test
    void handlesReplyToMessageWithoutSupportedPayload() {
        Map<String, Object> update = Map.of(
            "update_id", 100,
            "message", Map.of(
                "message_id", 13,
                "chat", Map.of("id", 3),
                "from", Map.of("id", 5),
                "text", "跟進一下",
                "reply_to_message", Map.of(
                    "message_id", 11
                )
            )
        );

        InboundMessage message = parser.parseIncomingTelegramMessage(update);

        assertNotNull(message);
        assertEquals(List.of(), message.replyToImageFileIds());
        assertEquals(11L, message.replyToMessageId());
        assertNull(message.replyToText());
    }

    @Test
    void toleratesNullReplyHelpersWhenMessageIsMissing() {
        // After refactoring to use MessageExtractor with Optional,
        // null handling is done internally. Test that parser handles
        // messages without reply_to_message gracefully.
        Map<String, Object> update = Map.of(
            "update_id", 100,
            "message", Map.of(
                "message_id", 13,
                "chat", Map.of("id", 3),
                "from", Map.of("id", 5),
                "text", "No reply context"
            )
        );

        InboundMessage message = parser.parseIncomingTelegramMessage(update);

        assertNotNull(message);
        assertEquals(List.of(), message.replyToImageFileIds());
        assertNull(message.replyToMessageId());
        assertNull(message.replyToText());
    }
}
