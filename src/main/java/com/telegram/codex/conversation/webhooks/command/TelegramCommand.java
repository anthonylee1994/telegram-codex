package com.telegram.codex.conversation.webhooks.command;

import com.telegram.codex.conversation.webhooks.Decision;
import com.telegram.codex.telegram.InboundMessage;

public interface TelegramCommand {

    boolean matches(String text);

    Decision execute(InboundMessage message);

    int priority();
}
