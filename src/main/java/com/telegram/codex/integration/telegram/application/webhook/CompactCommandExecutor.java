package com.telegram.codex.integration.telegram.application.webhook;

import com.telegram.codex.conversation.application.JobSchedulerService;
import com.telegram.codex.conversation.application.session.SessionService;
import com.telegram.codex.conversation.domain.ConversationConstants;
import com.telegram.codex.conversation.domain.MessageConstants;
import com.telegram.codex.integration.telegram.domain.InboundMessage;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CompactCommandExecutor {

    private final SessionService sessionService;
    private final JobSchedulerService jobSchedulerService;
    private final TelegramCommandResponder responder;

    public CompactCommandExecutor(
        SessionService sessionService,
        JobSchedulerService jobSchedulerService,
        TelegramCommandResponder responder
    ) {
        this.sessionService = sessionService;
        this.jobSchedulerService = jobSchedulerService;
        this.responder = responder;
    }

    public void execute(InboundMessage message) {
        SessionService.SessionSnapshot snapshot = sessionService.snapshot(message.chatId());
        Optional<SessionService.SessionCompactResult> immediateResult = validate(snapshot);
        if (immediateResult.isPresent()) {
            responder.sendCompactResult(message, immediateResult.get());
            return;
        }
        jobSchedulerService.enqueueSessionCompact(message.chatId());
        responder.reply(message, MessageConstants.COMPACT_QUEUED_MESSAGE);
    }

    private Optional<SessionService.SessionCompactResult> validate(SessionService.SessionSnapshot snapshot) {
        if (!snapshot.active()) {
            return Optional.of(SessionService.SessionCompactResult.missingSession());
        }
        if (snapshot.messageCount() < ConversationConstants.MIN_TRANSCRIPT_SIZE_FOR_COMPACT) {
            return Optional.of(SessionService.SessionCompactResult.tooShort(snapshot.messageCount()));
        }
        return Optional.empty();
    }
}
