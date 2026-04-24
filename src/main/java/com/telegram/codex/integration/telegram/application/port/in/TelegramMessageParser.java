package com.telegram.codex.integration.telegram.application.port.in;

import com.telegram.codex.integration.telegram.domain.InboundMessage;
import com.telegram.codex.integration.telegram.domain.webhook.TelegramUpdate;

public interface TelegramMessageParser {

    InboundMessage parseIncomingTelegramMessage(TelegramUpdate update);
}
