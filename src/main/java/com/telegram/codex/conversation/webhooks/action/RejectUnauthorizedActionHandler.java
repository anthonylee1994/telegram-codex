package com.telegram.codex.conversation.webhooks.action;

import com.telegram.codex.constants.MessageConstants;
import com.telegram.codex.conversation.updates.ProcessedUpdateFlow;
import com.telegram.codex.conversation.webhooks.Decision;
import com.telegram.codex.telegram.TelegramClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RejectUnauthorizedActionHandler implements ActionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RejectUnauthorizedActionHandler.class);

    private final TelegramClient telegramClient;
    private final ProcessedUpdateFlow processedUpdateFlow;

    public RejectUnauthorizedActionHandler(TelegramClient telegramClient, ProcessedUpdateFlow processedUpdateFlow) {
        this.telegramClient = telegramClient;
        this.processedUpdateFlow = processedUpdateFlow;
    }

    @Override
    public Decision.Action handlesAction() {
        return Decision.Action.REJECT_UNAUTHORIZED;
    }

    @Override
    public void execute(Decision decision, Map<String, Object> update) {
        LOGGER.warn("Rejected unauthorized Telegram user chat_id={} user_id={}",
            decision.message().chatId(), decision.message().userId());
        telegramClient.sendMessage(decision.message().chatId(), MessageConstants.UNAUTHORIZED_MESSAGE, List.of(), false);
        processedUpdateFlow.markProcessed(decision.message());
    }
}
