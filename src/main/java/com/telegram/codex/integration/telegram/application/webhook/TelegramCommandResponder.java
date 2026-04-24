package com.telegram.codex.integration.telegram.application.webhook;

import com.telegram.codex.conversation.application.ProcessedUpdateService;
import com.telegram.codex.conversation.application.session.SessionService;
import com.telegram.codex.integration.telegram.application.CompactResultSender;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TelegramCommandResponder {

    private final ProcessedUpdateService processedUpdateService;
    private final TelegramGateway telegramClient;
    private final CompactResultSender compactResultSender;

    public TelegramCommandResponder(
        ProcessedUpdateService processedUpdateService,
        TelegramGateway telegramClient,
        CompactResultSender compactResultSender
    ) {
        this.processedUpdateService = processedUpdateService;
        this.telegramClient = telegramClient;
        this.compactResultSender = compactResultSender;
    }

    public void reply(InboundMessage message, String text) {
        telegramClient.sendMessage(message.chatId(), text, List.of(), true);
        processedUpdateService.markProcessed(message);
    }

    public void sendCompactResult(InboundMessage message, SessionService.SessionCompactResult result) {
        compactResultSender.send(message.chatId(), result);
        processedUpdateService.markProcessed(message);
    }
}
