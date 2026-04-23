package com.telegram.codex.conversation.application.webhook.action;

import com.telegram.codex.conversation.domain.Decision;

import java.util.Map;

public interface ActionHandler {

    Decision.Action handlesAction();

    void execute(Decision decision, Map<String, Object> update);
}
