package com.telegram.codex.conversation.webhooks.action;

import com.telegram.codex.conversation.webhooks.Decision;

import java.util.Map;

public interface ActionHandler {

    Decision.Action handlesAction();

    void execute(Decision decision, Map<String, Object> update);
}
