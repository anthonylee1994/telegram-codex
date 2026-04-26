package com.telegram.codex.interfaces.web

import com.telegram.codex.integration.telegram.application.webhook.TelegramWebhookHandler
import com.telegram.codex.integration.telegram.domain.webhook.TelegramUpdate
import com.telegram.codex.shared.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

data class ApiStatusResponse(
    val ok: Boolean,
)

@RestController
class HealthController {
    @GetMapping("/health")
    fun show(): ApiStatusResponse = ApiStatusResponse(true)
}

@RestController
class TelegramWebhookController(
    private val properties: AppProperties,
    private val webhookHandler: TelegramWebhookHandler,
) {
    @PostMapping("/telegram/webhook")
    fun create(
        @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) secretToken: String?,
        @RequestBody(required = false) payload: TelegramUpdate?,
    ): ResponseEntity<ApiStatusResponse> {
        if (properties.telegramWebhookSecret != secretToken) {
            LOGGER.warn("Rejected Telegram webhook with invalid secret")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiStatusResponse(false))
        }
        return try {
            webhookHandler.handle(payload)
            ResponseEntity.ok(ApiStatusResponse(true))
        } catch (error: Exception) {
            LOGGER.error("Failed to process Telegram webhook: {}", error.message, error)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiStatusResponse(false))
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(TelegramWebhookController::class.java)
    }
}
