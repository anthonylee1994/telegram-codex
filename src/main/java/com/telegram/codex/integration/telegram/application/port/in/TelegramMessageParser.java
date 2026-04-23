package com.telegram.codex.integration.telegram.application.port.in;

import com.telegram.codex.integration.telegram.domain.InboundMessage;

import java.util.Map;

public interface TelegramMessageParser {

    InboundMessage parseIncomingTelegramMessage(Map<String, Object> update);
}
