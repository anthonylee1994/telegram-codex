package com.telegram.codex.conversation.webhooks.command;

import com.telegram.codex.constants.MessageConstants;
import com.telegram.codex.conversation.webhooks.Decision;
import com.telegram.codex.telegram.InboundMessage;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class ForgetCommand implements TelegramCommand {

    private static final Pattern PATTERN = Pattern.compile("^/forget(?:@[\\w_]+)?$", Pattern.UNICODE_CASE);

    @Override
    public boolean matches(String text) {
        return PATTERN.matcher(text).matches();
    }

    @Override
    public Decision execute(InboundMessage message) {
        return Decision.resetMemory(message, MessageConstants.RESET_MEMORY_MESSAGE);
    }

    @Override
    public int priority() {
        return 70;
    }
}
