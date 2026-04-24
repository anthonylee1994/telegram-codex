package com.telegram.codex.integration.telegram.application.webhook;

import com.telegram.codex.conversation.application.JobSchedulerService;
import com.telegram.codex.conversation.application.ProcessedUpdateService;
import com.telegram.codex.conversation.application.session.SessionService;
import com.telegram.codex.conversation.domain.ConversationConstants;
import com.telegram.codex.conversation.domain.MessageConstants;
import com.telegram.codex.integration.telegram.application.CompactResultSender;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class TelegramCommandHandler {

    private final TelegramCommandRegistry commandRegistry;
    private final SessionService sessionService;
    private final ProcessedUpdateService processedUpdateService;
    private final TelegramStatusMessageBuilder messageBuilder;
    private final TelegramGateway telegramClient;
    private final CompactResultSender compactResultSender;
    private final JobSchedulerService jobSchedulerService;

    public TelegramCommandHandler(
        TelegramCommandRegistry commandRegistry,
        SessionService sessionService,
        ProcessedUpdateService processedUpdateService,
        TelegramStatusMessageBuilder messageBuilder,
        TelegramGateway telegramClient,
        CompactResultSender compactResultSender,
        JobSchedulerService jobSchedulerService
    ) {
        this.commandRegistry = commandRegistry;
        this.sessionService = sessionService;
        this.processedUpdateService = processedUpdateService;
        this.messageBuilder = messageBuilder;
        this.telegramClient = telegramClient;
        this.compactResultSender = compactResultSender;
        this.jobSchedulerService = jobSchedulerService;
    }

    public boolean handle(InboundMessage message) {
        Optional<TelegramCommandRegistry.TelegramCommand> command = commandRegistry.resolve(message);
        if (command.isEmpty()) {
            return false;
        }
        executeCommand(command.get(), message);
        return true;
    }

    private void executeCommand(TelegramCommandRegistry.TelegramCommand command, InboundMessage message) {
        switch (command) {
            case START -> {
                sessionService.reset(message.chatId());
                sendAndMarkProcessed(message, MessageConstants.START_MESSAGE);
            }
            case NEW_SESSION -> {
                sessionService.reset(message.chatId());
                sendAndMarkProcessed(message, MessageConstants.NEW_SESSION_MESSAGE);
            }
            case HELP -> sendAndMarkProcessed(message, MessageConstants.HELP_MESSAGE);
            case STATUS -> sendAndMarkProcessed(message, messageBuilder.buildStatusMessage(message.chatId()));
            case SESSION -> sendAndMarkProcessed(message, messageBuilder.buildSessionMessage(message.chatId()));
            case MEMORY -> sendAndMarkProcessed(message, messageBuilder.buildMemoryMessage(message.chatId()));
            case FORGET -> {
                sessionService.resetMemory(message.chatId());
                sendAndMarkProcessed(message, MessageConstants.RESET_MEMORY_MESSAGE);
            }
            case COMPACT -> executeCompactSession(message);
        }
    }

    private void executeCompactSession(InboundMessage message) {
        SessionService.SessionSnapshot snapshot = sessionService.snapshot(message.chatId());
        if (!snapshot.active()) {
            compactResultSender.send(message.chatId(), SessionService.SessionCompactResult.missingSession());
            processedUpdateService.markProcessed(message);
            return;
        }
        if (snapshot.messageCount() < ConversationConstants.MIN_TRANSCRIPT_SIZE_FOR_COMPACT) {
            compactResultSender.send(message.chatId(), SessionService.SessionCompactResult.tooShort(snapshot.messageCount()));
            processedUpdateService.markProcessed(message);
            return;
        }
        jobSchedulerService.enqueueSessionCompact(message.chatId());
        telegramClient.sendMessage(message.chatId(), MessageConstants.COMPACT_QUEUED_MESSAGE, List.of(), true);
        processedUpdateService.markProcessed(message);
    }

    private void sendAndMarkProcessed(InboundMessage message, String text) {
        telegramClient.sendMessage(message.chatId(), text, List.of(), true);
        processedUpdateService.markProcessed(message);
    }
}
