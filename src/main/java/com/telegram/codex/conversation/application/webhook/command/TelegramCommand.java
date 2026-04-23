package com.telegram.codex.conversation.application.webhook.command;

import com.telegram.codex.conversation.domain.Decision;
import com.telegram.codex.integration.telegram.domain.InboundMessage;

public interface TelegramCommand {

    boolean matches(String text);

    Decision execute(InboundMessage message);

    int priority();
}
