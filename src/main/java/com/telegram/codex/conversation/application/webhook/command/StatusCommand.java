package com.telegram.codex.conversation.application.webhook.command;

import com.telegram.codex.conversation.domain.Decision;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class StatusCommand implements TelegramCommand {

    private static final Pattern PATTERN = Pattern.compile("^/status(?:@[\\w_]+)?$", Pattern.UNICODE_CASE);

    @Override
    public boolean matches(String text) {
        return PATTERN.matcher(text).matches();
    }

    @Override
    public Decision execute(InboundMessage message) {
        return Decision.showStatus(message);
    }

    @Override
    public int priority() {
        return 40;
    }
}
