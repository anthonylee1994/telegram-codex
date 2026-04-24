package com.telegram.codex.integration.telegram.infrastructure;

import com.telegram.codex.integration.telegram.domain.InboundMessage;
import com.telegram.codex.integration.telegram.domain.webhook.TelegramChat;
import com.telegram.codex.integration.telegram.domain.webhook.TelegramDocument;
import com.telegram.codex.integration.telegram.domain.webhook.TelegramMessage;
import com.telegram.codex.integration.telegram.domain.webhook.TelegramPhotoSize;
import com.telegram.codex.integration.telegram.domain.webhook.TelegramUpdate;
import com.telegram.codex.integration.telegram.domain.webhook.TelegramUser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TelegramUpdateParserTest {

    private final TelegramUpdateParser parser = new TelegramUpdateParser();

    @Test
    void parsesPhotoMessageAndReplyContext() {
        TelegramUpdate update = new TelegramUpdate(
            99L,
            new TelegramMessage(
                12L,
                new TelegramChat(3L),
                new TelegramUser(5L),
                null,
                "睇下呢張圖",
                null,
                List.of(
                    new TelegramPhotoSize("small", 100L),
                    new TelegramPhotoSize("large", 200L)
                ),
                null,
                new TelegramMessage(
                    10L,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new TelegramDocument("doc-1", "application/pdf", "a.pdf"),
                    null
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
        TelegramUpdate update = new TelegramUpdate(
            99L,
            new TelegramMessage(
                12L,
                new TelegramChat(3L),
                new TelegramUser(5L),
                null,
                null,
                null,
                null,
                null,
                null
            )
        );

        assertNull(parser.parseIncomingTelegramMessage(update));
    }

    @Test
    void ignoresUnsupportedDocuments() {
        TelegramUpdate update = new TelegramUpdate(
            101L,
            new TelegramMessage(
                14L,
                new TelegramChat(3L),
                new TelegramUser(5L),
                null,
                null,
                null,
                null,
                new TelegramDocument(
                    "doc-2",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "brief.docx"
                ),
                null
            )
        );

        assertNull(parser.parseIncomingTelegramMessage(update));
    }

    @Test
    void handlesReplyToMessageWithoutSupportedPayload() {
        TelegramUpdate update = new TelegramUpdate(
            100L,
            new TelegramMessage(
                13L,
                new TelegramChat(3L),
                new TelegramUser(5L),
                "跟進一下",
                null,
                null,
                null,
                null,
                new TelegramMessage(11L, null, null, null, null, null, null, null, null)
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
        TelegramUpdate update = new TelegramUpdate(
            100L,
            new TelegramMessage(
                13L,
                new TelegramChat(3L),
                new TelegramUser(5L),
                "No reply context",
                null,
                null,
                null,
                null,
                null
            )
        );

        InboundMessage message = parser.parseIncomingTelegramMessage(update);

        assertNotNull(message);
        assertEquals(List.of(), message.replyToImageFileIds());
        assertNull(message.replyToMessageId());
        assertNull(message.replyToText());
    }
}
