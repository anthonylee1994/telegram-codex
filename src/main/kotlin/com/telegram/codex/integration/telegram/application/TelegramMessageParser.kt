package com.telegram.codex.integration.telegram.application

import com.telegram.codex.integration.telegram.domain.InboundMessage
import com.telegram.codex.integration.telegram.domain.TelegramUpdate

interface TelegramMessageParser {
    fun parseIncomingTelegramMessage(update: TelegramUpdate?): InboundMessage?
}
