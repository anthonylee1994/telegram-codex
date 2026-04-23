package com.telegram.codex.conversation.application.webhook.action;

import com.telegram.codex.conversation.domain.Decision;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ActionHandlerRegistry {

    private final Map<Decision.Action, ActionHandler> handlers;

    public ActionHandlerRegistry(List<ActionHandler> handlerList) {
        this.handlers = handlerList.stream()
            .collect(Collectors.toMap(ActionHandler::handlesAction, Function.identity()));
    }

    public void execute(Decision decision, Map<String, Object> update) {
        ActionHandler handler = handlers.get(decision.action());
        if (handler == null) {
            throw new IllegalStateException("No handler for action: " + decision.action());
        }
        handler.execute(decision, update);
    }
}
