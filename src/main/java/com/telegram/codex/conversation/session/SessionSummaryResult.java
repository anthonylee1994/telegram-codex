package com.telegram.codex.conversation.session;

public record SessionSummaryResult(Status status, Integer messageCount, Integer originalMessageCount, String summaryText) {

    public enum Status {
        MISSING_SESSION,
        TOO_SHORT,
        OK
    }

    public static SessionSummaryResult missingSession() {
        return new SessionSummaryResult(Status.MISSING_SESSION, null, null, null);
    }

    public static SessionSummaryResult tooShort(int messageCount) {
        return new SessionSummaryResult(Status.TOO_SHORT, messageCount, null, null);
    }

    public static SessionSummaryResult ok(int originalMessageCount, String summaryText) {
        return new SessionSummaryResult(Status.OK, null, originalMessageCount, summaryText);
    }
}
