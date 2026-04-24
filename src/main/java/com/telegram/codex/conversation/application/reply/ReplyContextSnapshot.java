package com.telegram.codex.conversation.application.reply;

public record ReplyContextSnapshot(
    String promptText,
    String lastResponseId,
    String replyToText,
    String memoryText
) {
}
