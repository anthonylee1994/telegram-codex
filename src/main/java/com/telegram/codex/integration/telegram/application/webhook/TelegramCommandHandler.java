package com.telegram.codex.integration.telegram.application.webhook;

import com.telegram.codex.conversation.application.session.SessionService;
import com.telegram.codex.conversation.domain.MessageConstants;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class TelegramCommandHandler {

    private final TelegramCommandRegistry commandRegistry;
    private final SessionService sessionService;
    private final TelegramStatusMessageBuilder messageBuilder;
    private final TelegramCommandResponder responder;
    private final CompactCommandExecutor compactCommandExecutor;

    public TelegramCommandHandler(
        TelegramCommandRegistry commandRegistry,
        SessionService sessionService,
        TelegramStatusMessageBuilder messageBuilder,
        TelegramCommandResponder responder,
        CompactCommandExecutor compactCommandExecutor
    ) {
        this.commandRegistry = commandRegistry;
        this.sessionService = sessionService;
        this.messageBuilder = messageBuilder;
        this.responder = responder;
        this.compactCommandExecutor = compactCommandExecutor;
    }

    public boolean handle(InboundMessage message) {
        Optional<TelegramCommandRegistry.TelegramCommand> command = commandRegistry.resolve(message);
        if (command.isEmpty()) {
            return false;
        }
        execute(command.get(), message);
        return true;
    }

    private void execute(TelegramCommandRegistry.TelegramCommand command, InboundMessage message) {
        switch (command) {
            case START -> {
                sessionService.reset(message.chatId());
                responder.reply(message, MessageConstants.START_MESSAGE);
            }
            case NEW_SESSION -> {
                sessionService.reset(message.chatId());
                responder.reply(message, MessageConstants.NEW_SESSION_MESSAGE);
            }
            case HELP -> responder.reply(message, MessageConstants.HELP_MESSAGE);
            case STATUS -> responder.reply(message, messageBuilder.buildStatusMessage(message.chatId()));
            case SESSION -> responder.reply(message, messageBuilder.buildSessionMessage(message.chatId()));
            case MEMORY -> responder.reply(message, messageBuilder.buildMemoryMessage(message.chatId()));
            case FORGET -> {
                sessionService.resetMemory(message.chatId());
                responder.reply(message, MessageConstants.RESET_MEMORY_MESSAGE);
            }
            case COMPACT -> compactCommandExecutor.execute(message);
        }
    }
}
