package com.telegram.codex.conversation.domain.session;

public record SessionCompactResult(Status status, Integer messageCount, Integer originalMessageCount, String compactText) {

    public enum Status {
        MISSING_SESSION,
        TOO_SHORT,
        OK
    }

    public static SessionCompactResult missingSession() {
        return new SessionCompactResult(Status.MISSING_SESSION, null, null, null);
    }

    public static SessionCompactResult tooShort(int messageCount) {
        return new SessionCompactResult(Status.TOO_SHORT, messageCount, null, null);
    }

    public static SessionCompactResult ok(int originalMessageCount, String compactText) {
        return new SessionCompactResult(Status.OK, null, originalMessageCount, compactText);
    }
}
