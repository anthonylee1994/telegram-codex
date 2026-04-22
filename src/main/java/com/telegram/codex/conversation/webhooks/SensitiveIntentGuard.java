package com.telegram.codex.conversation.webhooks;

import com.telegram.codex.telegram.InboundMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class SensitiveIntentGuard {

    private static final List<String> SENSITIVE_PATTERNS = List.of(
        "code base",
        "codebase",
        "repo",
        "repository",
        "source code",
        "project files",
        "local files",
        "internal files",
        "system prompt",
        "hidden prompt",
        "hidden instructions",
        "scan the project",
        "scan project",
        "inspect the repo",
        "read the repo",
        "review the repo",
        "find bugs in your code",
        "bugs in your code",
        "look through the codebase",
        "睇下你個 code base",
        "睇下你 code base",
        "你 code base",
        "你個 repo",
        "你 repo",
        "內部檔案",
        "本機檔案",
        "source code 有咩 bugs",
        "程式碼有咩 bugs",
        "系統提示",
        "隱藏指示"
    );

    public boolean shouldBlock(InboundMessage message) {
        if (message == null) {
            return false;
        }
        return containsSensitivePattern(message.text()) || containsSensitivePattern(message.replyToText());
    }

    private boolean containsSensitivePattern(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return SENSITIVE_PATTERNS.stream().anyMatch(normalized::contains);
    }
}
