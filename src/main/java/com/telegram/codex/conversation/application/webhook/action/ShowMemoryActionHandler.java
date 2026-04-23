package com.telegram.codex.conversation.application.webhook.action;

import com.telegram.codex.conversation.application.memory.MemoryService;
import com.telegram.codex.conversation.application.update.ProcessedUpdateFlow;
import com.telegram.codex.conversation.domain.Decision;
import com.telegram.codex.conversation.domain.memory.MemorySnapshot;
import com.telegram.codex.integration.telegram.application.port.out.TelegramGateway;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ShowMemoryActionHandler implements ActionHandler {

    private final MemoryService memoryService;
    private final TelegramGateway telegramClient;
    private final ProcessedUpdateFlow processedUpdateFlow;

    public ShowMemoryActionHandler(
        MemoryService memoryService,
        TelegramGateway telegramClient,
        ProcessedUpdateFlow processedUpdateFlow
    ) {
        this.memoryService = memoryService;
        this.telegramClient = telegramClient;
        this.processedUpdateFlow = processedUpdateFlow;
    }

    @Override
    public Decision.Action handlesAction() {
        return Decision.Action.SHOW_MEMORY;
    }

    @Override
    public void execute(Decision decision, Map<String, Object> update) {
        String memoryMessage = buildMemoryMessage(decision.message().chatId());
        telegramClient.sendMessage(decision.message().chatId(), memoryMessage, List.of(), true);
        processedUpdateFlow.markProcessed(decision.message());
    }

    private String buildMemoryMessage(String chatId) {
        MemorySnapshot snapshot = memoryService.snapshot(chatId);
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
}
