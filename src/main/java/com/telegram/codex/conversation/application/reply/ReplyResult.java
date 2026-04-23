package com.telegram.codex.conversation.application.reply;

import java.util.List;

public record ReplyResult(String conversationState, List<String> suggestedReplies, String text) {
}
