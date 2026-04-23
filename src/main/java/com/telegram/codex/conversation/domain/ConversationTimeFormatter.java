package com.telegram.codex.conversation.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ConversationTimeFormatter {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
        .withZone(ZoneId.of("Asia/Hong_Kong"));

    private ConversationTimeFormatter() {
    }

    public static String format(long epochMillis) {
        return FORMATTER.format(Instant.ofEpochMilli(epochMillis));
    }
}
