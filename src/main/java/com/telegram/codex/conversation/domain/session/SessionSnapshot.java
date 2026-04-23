package com.telegram.codex.conversation.domain.session;

public record SessionSnapshot(boolean active, int messageCount, int turnCount, String lastUpdatedAt) {

    public static SessionSnapshot inactive() {
        return new SessionSnapshot(false, 0, 0, null);
    }

    public static SessionSnapshot active(int messageCount, int turnCount, String lastUpdatedAt) {
        return new SessionSnapshot(true, messageCount, turnCount, lastUpdatedAt);
    }
}
