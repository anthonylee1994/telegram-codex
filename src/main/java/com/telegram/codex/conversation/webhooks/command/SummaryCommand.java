package com.telegram.codex.conversation.webhooks.command;

import com.telegram.codex.constants.MessageConstants;
import com.telegram.codex.conversation.webhooks.Decision;
import com.telegram.codex.telegram.InboundMessage;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class SummaryCommand implements TelegramCommand {

    private static final Pattern PATTERN = Pattern.compile("^/summary(?:@[\\w_]+)?$", Pattern.UNICODE_CASE);

    @Override
    public boolean matches(String text) {
        return PATTERN.matcher(text).matches();
    }

    @Override
    public Decision execute(InboundMessage message) {
        return Decision.summarizeSession(message, MessageConstants.SUMMARY_QUEUED_MESSAGE);
    }

    @Override
    public int priority() {
        return 80;
    }
}
