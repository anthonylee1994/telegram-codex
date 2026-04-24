package com.telegram.codex.integration.telegram.application.webhook;

import com.telegram.codex.conversation.application.ProcessedUpdateService;
import com.telegram.codex.conversation.domain.update.ProcessedUpdateRecord;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class DuplicateUpdateHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DuplicateUpdateHandler.class);

    private final ProcessedUpdateService processedUpdateService;
    private final TelegramGateway telegramClient;

    public DuplicateUpdateHandler(ProcessedUpdateService processedUpdateService, TelegramGateway telegramClient) {
        this.processedUpdateService = processedUpdateService;
        this.telegramClient = telegramClient;
    }

    public boolean handle(InboundMessage message) {
        Optional<ProcessedUpdateRecord> processedUpdate = processedUpdateService.find(message.updateId());
        if (processedUpdateService.duplicate(processedUpdate)) {
            LOGGER.info("Ignored duplicate update update_id={}", message.updateId());
            return true;
        }
        if (processedUpdateService.replayable(processedUpdate)) {
            processedUpdateService.resendPendingReply(message, processedUpdate.orElseThrow(), telegramClient);
            return true;
        }
        return false;
    }
}
