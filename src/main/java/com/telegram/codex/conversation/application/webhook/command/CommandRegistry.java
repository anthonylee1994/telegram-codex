package com.telegram.codex.conversation.application.webhook.command;

import com.telegram.codex.conversation.domain.Decision;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class CommandRegistry {

    private final List<TelegramCommand> commands;

    public CommandRegistry(List<TelegramCommand> commands) {
        this.commands = commands.stream()
            .sorted(Comparator.comparingInt(TelegramCommand::priority))
            .toList();
    }

    public Optional<Decision> executeCommand(InboundMessage message) {
        String text = message.text() == null ? "" : message.text();
        return commands.stream()
            .filter(cmd -> cmd.matches(text))
            .findFirst()
            .map(cmd -> cmd.execute(message));
    }
}
