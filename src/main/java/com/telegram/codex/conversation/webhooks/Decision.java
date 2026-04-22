package com.telegram.codex.conversation.webhooks;

import com.telegram.codex.conversation.updates.ProcessedUpdateRecord;
import com.telegram.codex.telegram.InboundMessage;

public record Decision(Action action, InboundMessage message, ProcessedUpdateRecord processedUpdate, String responseText) {

    public enum Action {
        UNSUPPORTED,
        DUPLICATE,
        REPLAY,
        REJECT_UNAUTHORIZED,
        RESET_SESSION,
        SHOW_HELP,
        SHOW_STATUS,
        SHOW_SESSION,
        SHOW_MEMORY,
        RESET_MEMORY,
        SUMMARIZE_SESSION,
        RATE_LIMITED,
        TOO_MANY_IMAGES,
        REJECT_SENSITIVE_INTENT,
        GENERATE_REPLY
    }

    public static Decision unsupported() {
        return new Decision(Action.UNSUPPORTED, null, null, null);
    }

    public static Decision duplicate(InboundMessage message) {
        return new Decision(Action.DUPLICATE, message, null, null);
    }

    public static Decision replay(InboundMessage message, ProcessedUpdateRecord processedUpdate) {
        return new Decision(Action.REPLAY, message, processedUpdate, null);
    }

    public static Decision rejectUnauthorized(InboundMessage message) {
        return new Decision(Action.REJECT_UNAUTHORIZED, message, null, null);
    }

    public static Decision resetSession(InboundMessage message, String responseText) {
        return new Decision(Action.RESET_SESSION, message, null, responseText);
    }

    public static Decision showHelp(InboundMessage message) {
        return new Decision(Action.SHOW_HELP, message, null, null);
    }

    public static Decision showStatus(InboundMessage message) {
        return new Decision(Action.SHOW_STATUS, message, null, null);
    }

    public static Decision showSession(InboundMessage message) {
        return new Decision(Action.SHOW_SESSION, message, null, null);
    }

    public static Decision showMemory(InboundMessage message) {
        return new Decision(Action.SHOW_MEMORY, message, null, null);
    }

    public static Decision resetMemory(InboundMessage message, String responseText) {
        return new Decision(Action.RESET_MEMORY, message, null, responseText);
    }

    public static Decision summarizeSession(InboundMessage message, String responseText) {
        return new Decision(Action.SUMMARIZE_SESSION, message, null, responseText);
    }

    public static Decision rateLimited(InboundMessage message) {
        return new Decision(Action.RATE_LIMITED, message, null, null);
    }

    public static Decision tooManyImages(InboundMessage message, String responseText) {
        return new Decision(Action.TOO_MANY_IMAGES, message, null, responseText);
    }

    public static Decision rejectSensitiveIntent(InboundMessage message, String responseText) {
        return new Decision(Action.REJECT_SENSITIVE_INTENT, message, null, responseText);
    }

    public static Decision generateReply(InboundMessage message) {
        return new Decision(Action.GENERATE_REPLY, message, null, null);
    }
}
