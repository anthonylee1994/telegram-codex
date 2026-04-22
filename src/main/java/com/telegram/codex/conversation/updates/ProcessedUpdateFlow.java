package com.telegram.codex.conversation.updates;

import com.telegram.codex.codex.ReplyParser;
import com.telegram.codex.conversation.reply.ConversationService;
import com.telegram.codex.conversation.session.SessionService;
import com.telegram.codex.telegram.InboundMessage;
import com.telegram.codex.telegram.TelegramClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProcessedUpdateFlow {

    private final ConversationService conversationService;
    private final ReplyParser replyParser;
    private final SessionService sessionService;

    public ProcessedUpdateFlow(ConversationService conversationService, ReplyParser replyParser, SessionService sessionService) {
        this.conversationService = conversationService;
        this.replyParser = replyParser;
        this.sessionService = sessionService;
    }

    public Optional<ProcessedUpdateRecord> find(long updateId) {
        return conversationService.getProcessedUpdate(updateId);
    }

    public boolean beginProcessing(InboundMessage message) {
        ArrayList<Long> claimedUpdateIds = new ArrayList<>();
        for (InboundMessage.ProcessingUpdate processingUpdate : message.processingUpdates()) {
            boolean claimed = conversationService.beginProcessing(
                processingUpdate.updateId(),
                message.chatId(),
                processingUpdate.messageId()
            );
            if (!claimed) {
                rollbackProcessingClaims(claimedUpdateIds);
                return false;
            }
            claimedUpdateIds.add(processingUpdate.updateId());
        }
        return true;
    }

    public boolean duplicate(Optional<ProcessedUpdateRecord> processedUpdate) {
        return processedUpdate.map(ProcessedUpdateRecord::sentAt).orElse(null) != null;
    }

    public boolean replayable(Optional<ProcessedUpdateRecord> processedUpdate) {
        return processedUpdate
            .filter(update -> update.replyText() != null && update.conversationState() != null)
            .isPresent();
    }

    public void resendPendingReply(InboundMessage message, ProcessedUpdateRecord processedUpdate, TelegramClient telegramClient) {
        telegramClient.sendMessage(
            message.chatId(),
            processedUpdate.replyText(),
            replyParser.parseSuggestedReplies(processedUpdate.suggestedReplies()),
            false
        );
        sessionService.persistConversationState(message.chatId(), processedUpdate.conversationState());
        markProcessed(message);
    }

    public void markProcessed(InboundMessage message) {
        for (InboundMessage.ProcessingUpdate processingUpdate : message.processingUpdates()) {
            conversationService.markProcessed(processingUpdate.updateId(), message.chatId(), processingUpdate.messageId());
        }
    }

    private void rollbackProcessingClaims(List<Long> claimedUpdateIds) {
        for (Long updateId : claimedUpdateIds) {
            conversationService.clearProcessing(updateId);
        }
    }
}
