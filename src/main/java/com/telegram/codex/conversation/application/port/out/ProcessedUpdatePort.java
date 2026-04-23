package com.telegram.codex.conversation.application.port.out;

import com.telegram.codex.conversation.application.reply.ReplyResult;
import com.telegram.codex.conversation.domain.update.ProcessedUpdateRecord;

import java.util.Optional;

public interface ProcessedUpdatePort {

    Optional<ProcessedUpdateRecord> find(long updateId);

    boolean beginProcessing(long updateId, String chatId, long messageId);

    void clearProcessing(long updateId);

    void markProcessed(long updateId, String chatId, long messageId);

    void savePendingReply(long updateId, String chatId, long messageId, ReplyResult result);

    long pruneSentBefore(long cutoff);
}
