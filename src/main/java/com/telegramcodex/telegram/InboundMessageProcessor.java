package com.telegramcodex.telegram;

import com.telegramcodex.conversation.webhooks.ActionExecutor;
import com.telegramcodex.conversation.webhooks.Decision;
import com.telegramcodex.conversation.webhooks.DecisionResolver;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

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
