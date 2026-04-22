package com.telegramcodex.conversation;

import java.util.List;

public record ReplyResult(String conversationState, List<String> suggestedReplies, String text) {
}
