package com.telegram.codex.integration.telegram.application;

import com.telegram.codex.conversation.application.webhook.ActionExecutor;
import com.telegram.codex.conversation.application.webhook.DecisionResolver;
import com.telegram.codex.conversation.domain.Decision;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class InboundMessageProcessor {

    private final ActionExecutor actionExecutor;
    private final DecisionResolver decisionResolver;

    public InboundMessageProcessor(ActionExecutor actionExecutor, DecisionResolver decisionResolver) {
        this.actionExecutor = actionExecutor;
        this.decisionResolver = decisionResolver;
    }

    public void process(InboundMessage message) {
        process(message, null);
    }

    public void process(InboundMessage message, Map<String, Object> update) {
        Decision decision = decisionResolver.call(message);
        actionExecutor.call(decision, update);
    }

    public Object deferMediaGroup(InboundMessage message, Duration waitDuration) {
        return actionExecutor.deferMediaGroup(message, waitDuration);
    }
}
