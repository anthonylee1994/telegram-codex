package com.telegram.codex.constants;

public final class ConversationConstants {

    private ConversationConstants() {
        // Constants class
    }

    public static final int MAX_TRANSCRIPT_MESSAGES = 100;
    public static final int MIN_TRANSCRIPT_SIZE_FOR_SUMMARY = 4;
    public static final long PROCESSED_UPDATE_PRUNE_INTERVAL_MS = 6L * 60 * 60 * 1000;
    public static final long PROCESSED_UPDATE_RETENTION_MS = 30L * 24 * 60 * 60 * 1000;
}
