package com.telegram.codex.integration.telegram.application.webhook;

import com.telegram.codex.conversation.application.session.SessionService;
import org.springframework.stereotype.Component;

@Component
public class TelegramStatusMessageBuilder {

    private final SessionService sessionService;

    public TelegramStatusMessageBuilder(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public String buildStatusMessage(String chatId) {
        SessionService.SessionSnapshot snapshot = sessionService.snapshot(chatId);
        return String.join("\n",
            "Bot 狀態：OK 🤖",
            "Session 狀態：" + renderSessionStatus(snapshot),
            "只支持：文字、圖片"
        );
    }

    public String buildSessionMessage(String chatId) {
        SessionService.SessionSnapshot snapshot = sessionService.snapshot(chatId);
        if (!snapshot.active()) {
            return "目前未有已生效 session。你可以直接 send 訊息開始，或者之後打 /compact 壓縮長對話。";
        }
        return String.join("\n",
            "目前 session：已生效",
            "訊息數：" + snapshot.messageCount(),
            "大概輪數：" + snapshot.turnCount(),
            "最後更新：" + snapshot.lastUpdatedAt(),
            "想壓縮 context 可以打 /compact。"
        );
    }

    public String buildMemoryMessage(String chatId) {
        SessionService.MemorySnapshot snapshot = sessionService.memorySnapshot(chatId);
        if (!snapshot.active()) {
            return "目前未有長期記憶。你可以直接叫我記住、改寫或者刪除長期記憶；我之後亦會自動記低穩定偏好同持續背景。想清除可以打 /forget。";
        }
        return String.join("\n",
            "長期記憶：已生效",
            "最後更新：" + snapshot.lastUpdatedAt(),
            "",
            snapshot.memoryText(),
            "",
            "你可以直接叫我改寫或者刪除長期記憶。",
            "想清除可以打 /forget。"
        );
    }

    private String renderSessionStatus(SessionService.SessionSnapshot snapshot) {
        return snapshot.active() ? "已生效 ✅" : "未生效 ❌";
    }
}
