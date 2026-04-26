package com.telegram.codex.interfaces.web

import com.telegram.codex.integration.telegram.application.TelegramWebhookHandler
import com.telegram.codex.integration.telegram.domain.TelegramUpdate
import com.telegram.codex.shared.AppProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class TelegramWebhookControllerTest {
    @Test
    fun rejectsInvalidSecret() {
        val controller = TelegramWebhookController(properties(), Mockito.mock(TelegramWebhookHandler::class.java))

        val response = controller.create("bad", TelegramUpdate(1L, null))

        assertEquals(401, response.statusCode.value())
        assertEquals(false, response.body!!.ok)
    }

    @Test
    fun acceptsValidSecret() {
        val handler = Mockito.mock(TelegramWebhookHandler::class.java)
        val controller = TelegramWebhookController(properties(), handler)
        val payload = TelegramUpdate(1L, null)

        val response = controller.create("secret", payload)

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body!!.ok)
        Mockito.verify(handler).handle(payload)
    }

    @Test
    fun returnsInternalServerErrorWhenHandlerFails() {
        val handler = Mockito.mock(TelegramWebhookHandler::class.java)
        val payload = TelegramUpdate(1L, null)
        Mockito.doThrow(IllegalStateException("boom")).`when`(handler).handle(payload)
        val controller = TelegramWebhookController(properties(), handler)

        val response = controller.create("secret", payload)

        assertEquals(500, response.statusCode.value())
        assertEquals(false, response.body!!.ok)
    }

    private fun properties(): AppProperties = AppProperties().apply {
        baseUrl = "https://example.com"
        telegramBotToken = "token"
        telegramWebhookSecret = "secret"
    }
}
