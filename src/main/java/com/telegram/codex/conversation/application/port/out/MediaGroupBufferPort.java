package com.telegram.codex.conversation.application.port.out;

import com.telegram.codex.integration.telegram.domain.InboundMessage;

public interface MediaGroupBufferPort {

    EnqueueResult enqueue(InboundMessage message, double waitDurationSeconds);

    FlushResult flush(String key, long expectedDeadlineAt);

    void clear();

    record EnqueueResult(long deadlineAt, String key) {
    }

    record FlushResult(String status, InboundMessage message, Double waitDurationSeconds) {
        public static FlushResult missing() {
            return new FlushResult("missing", null, null);
        }

        public static FlushResult stale() {
            return new FlushResult("stale", null, null);
        }

        public static FlushResult pending(double waitDurationSeconds) {
            return new FlushResult("pending", null, waitDurationSeconds);
        }

        public static FlushResult ready(InboundMessage message) {
            return new FlushResult("ready", message, null);
        }
    }
}
