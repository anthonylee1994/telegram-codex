package com.telegram.codex.integration.telegram.infrastructure

import com.telegram.codex.integration.telegram.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TelegramUpdateParserTest {
    private val parser = TelegramUpdateParser()

    @Test
    fun parsesPhotoMessageAndReplyContext() {
        val update = TelegramUpdate(
            99L,
            TelegramMessage(
                12L,
                TelegramChat(3L),
                TelegramUser(5L),
                null,
                "睇下呢張圖",
                null,
                listOf(TelegramPhotoSize("small", 100L), TelegramPhotoSize("large", 200L)),
                null,
                TelegramMessage(10L, null, null, null, null, null, null, TelegramDocument("doc-1", "application/pdf", "a.pdf"), null),
            ),
        )

        val message = requireNotNull(parser.parseIncomingTelegramMessage(update))

        assertEquals("3", message.chatId)
        assertEquals(listOf("large"), message.imageFileIds)
        assertNull(message.replyToText)
    }

    @Test
    fun ignoresUnsupportedMessages() {
        val update = TelegramUpdate(99L, TelegramMessage(12L, TelegramChat(3L), TelegramUser(5L), null, null, null, null, null, null))
        assertNull(parser.parseIncomingTelegramMessage(update))
    }
}
