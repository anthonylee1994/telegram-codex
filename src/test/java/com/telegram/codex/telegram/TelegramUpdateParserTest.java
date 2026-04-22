package com.telegram.codex.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
        assertEquals("doc-1", message.replyToPdfFileId());
        assertEquals("用戶引用咗一份 PDF。", message.replyToText());
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
        assertNull(message.replyToPdfFileId());
        assertNull(message.replyToText());
        assertNull(message.replyToTextDocumentFileId());
        assertNull(message.replyToTextDocumentName());
    }

    @Test
    void toleratesNullReplyHelpersWhenMessageIsMissing() {
        assertEquals(List.of(), invoke("buildReplyToImageFileIds", null));
        assertNull(invoke("buildReplyToMessageId", null));
        assertNull(invoke("buildReplyToPdfFileId", null));
        assertNull(invoke("buildReplyToText", null));
        assertNull(invoke("buildReplyToTextDocumentFileId", null));
        assertNull(invoke("buildReplyToTextDocumentName", null));
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(String methodName, Map<String, Object> message) {
        try {
            Method method = TelegramUpdateParser.class.getDeclaredMethod(methodName, Map.class);
            method.setAccessible(true);
            return (T) method.invoke(parser, message);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }
}
