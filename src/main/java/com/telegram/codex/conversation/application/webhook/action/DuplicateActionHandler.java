package com.telegram.codex.conversation.application.webhook.action;

import com.telegram.codex.conversation.domain.Decision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DuplicateActionHandler implements ActionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DuplicateActionHandler.class);

    @Override
    public Decision.Action handlesAction() {
        return Decision.Action.DUPLICATE;
    }

    @Override
    public void execute(Decision decision, Map<String, Object> update) {
        LOGGER.info("Ignored duplicate update update_id={}", decision.message().updateId());
    }
}
