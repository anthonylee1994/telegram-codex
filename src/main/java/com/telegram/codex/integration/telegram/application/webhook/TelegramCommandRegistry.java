package com.telegram.codex.integration.telegram.application.webhook;

import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class TelegramCommandRegistry {

    private static final List<CommandSpec> COMMANDS = List.of(
        new CommandSpec(Pattern.compile("^/start(?:@[\\w_]+)?$", Pattern.UNICODE_CASE), TelegramCommand.START),
        new CommandSpec(Pattern.compile("^/new(?:@[\\w_]+)?$", Pattern.UNICODE_CASE), TelegramCommand.NEW_SESSION),
        new CommandSpec(Pattern.compile("^/help(?:@[\\w_]+)?$", Pattern.UNICODE_CASE), TelegramCommand.HELP),
        new CommandSpec(Pattern.compile("^/status(?:@[\\w_]+)?$", Pattern.UNICODE_CASE), TelegramCommand.STATUS),
        new CommandSpec(Pattern.compile("^/session(?:@[\\w_]+)?$", Pattern.UNICODE_CASE), TelegramCommand.SESSION),
        new CommandSpec(Pattern.compile("^/memory(?:@[\\w_]+)?$", Pattern.UNICODE_CASE), TelegramCommand.MEMORY),
        new CommandSpec(Pattern.compile("^/forget(?:@[\\w_]+)?$", Pattern.UNICODE_CASE), TelegramCommand.FORGET),
        new CommandSpec(Pattern.compile("^/compact(?:@[\\w_]+)?$", Pattern.UNICODE_CASE), TelegramCommand.COMPACT)
    );

    public TelegramCommandRegistry() {
    }

    public Optional<TelegramCommand> resolve(InboundMessage message) {
        return COMMANDS.stream()
            .filter(command -> command.matches(message.textOrEmpty()))
            .findFirst()
            .map(CommandSpec::command);
    }

    public enum TelegramCommand {
        START,
        NEW_SESSION,
        HELP,
        STATUS,
        SESSION,
        MEMORY,
        FORGET,
        COMPACT
    }

    private record CommandSpec(Pattern pattern, TelegramCommand command) {

        private boolean matches(String text) {
            return pattern.matcher(text).matches();
        }
    }
}
