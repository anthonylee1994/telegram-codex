package com.telegram.codex.conversation.webhooks;

import com.telegram.codex.conversation.MediaGroupStore;
import com.telegram.codex.conversation.memory.MemoryService;
import com.telegram.codex.conversation.memory.MemorySnapshot;
import com.telegram.codex.conversation.session.SessionService;
import com.telegram.codex.conversation.session.SessionSnapshot;
import com.telegram.codex.conversation.updates.ProcessedUpdateFlow;
import com.telegram.codex.jobs.JobSchedulerService;
import com.telegram.codex.telegram.InboundMessage;
import com.telegram.codex.telegram.TelegramClient;
import com.telegram.codex.telegram.TelegramWebhookHandler;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ActionExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActionExecutor.class);

    private final MemoryService memoryService;
    private final JobSchedulerService jobSchedulerService;
    private final MediaGroupStore mediaGroupStore;
    private final ProcessedUpdateFlow processedUpdateFlow;
    private final SessionService sessionService;
    private final TelegramClient telegramClient;

    public ActionExecutor(
        MemoryService memoryService,
        JobSchedulerService jobSchedulerService,
        MediaGroupStore mediaGroupStore,
        ProcessedUpdateFlow processedUpdateFlow,
        SessionService sessionService,
        TelegramClient telegramClient
    ) {
        this.memoryService = memoryService;
        this.jobSchedulerService = jobSchedulerService;
        this.mediaGroupStore = mediaGroupStore;
        this.processedUpdateFlow = processedUpdateFlow;
        this.sessionService = sessionService;
        this.telegramClient = telegramClient;
    }

    public void call(Decision decision, Map<String, Object> update) {
        if (decision.action() == Decision.Action.UNSUPPORTED) {
            if (update == null) {
                return;
            }
            String chatId = extractChatId(update);
            if (chatId != null) {
                telegramClient.sendMessage(chatId, TelegramWebhookHandler.UNSUPPORTED_MESSAGE, List.of(), false);
            }
            return;
        }

        InboundMessage message = decision.message();
        switch (decision.action()) {
            case DUPLICATE -> LOGGER.info("Ignored duplicate update update_id={}", message.updateId());
            case REPLAY -> processedUpdateFlow.resendPendingReply(message, decision.processedUpdate(), telegramClient);
            case REJECT_UNAUTHORIZED -> {
                LOGGER.warn("Rejected unauthorized Telegram user chat_id={} user_id={}", message.chatId(), message.userId());
                telegramClient.sendMessage(message.chatId(), TelegramWebhookHandler.UNAUTHORIZED_MESSAGE, List.of(), false);
                processedUpdateFlow.markProcessed(message);
            }
            case RESET_SESSION -> {
                sessionService.reset(message.chatId());
                telegramClient.sendMessage(message.chatId(), decision.responseText(), List.of(), true);
                processedUpdateFlow.markProcessed(message);
            }
            case SHOW_HELP -> {
                telegramClient.sendMessage(message.chatId(), TelegramWebhookHandler.HELP_MESSAGE, List.of(), true);
                processedUpdateFlow.markProcessed(message);
            }
            case SHOW_STATUS -> {
                telegramClient.sendMessage(message.chatId(), buildStatusMessage(message.chatId()), List.of(), true);
                processedUpdateFlow.markProcessed(message);
            }
            case SHOW_SESSION -> {
                telegramClient.sendMessage(message.chatId(), buildSessionMessage(message.chatId()), List.of(), true);
                processedUpdateFlow.markProcessed(message);
            }
            case SHOW_MEMORY -> {
                telegramClient.sendMessage(message.chatId(), buildMemoryMessage(message.chatId()), List.of(), true);
                processedUpdateFlow.markProcessed(message);
            }
            case RESET_MEMORY -> {
                memoryService.reset(message.chatId());
                telegramClient.sendMessage(message.chatId(), decision.responseText(), List.of(), true);
                processedUpdateFlow.markProcessed(message);
            }
            case SUMMARIZE_SESSION -> {
                jobSchedulerService.enqueueSessionSummary(message.chatId());
                telegramClient.sendMessage(message.chatId(), decision.responseText(), List.of(), true);
                processedUpdateFlow.markProcessed(message);
            }
            case RATE_LIMITED -> {
                telegramClient.sendMessage(message.chatId(), TelegramWebhookHandler.RATE_LIMIT_MESSAGE, List.of(), false);
                processedUpdateFlow.markProcessed(message);
            }
            case TOO_MANY_IMAGES -> {
                telegramClient.sendMessage(message.chatId(), decision.responseText(), List.of(), false);
                processedUpdateFlow.markProcessed(message);
            }
            case GENERATE_REPLY -> jobSchedulerService.enqueueReplyGeneration(message);
            default -> throw new IllegalStateException("Unhandled action " + decision.action());
        }
    }

    public Object deferMediaGroup(InboundMessage message, Duration waitDuration) {
        MediaGroupStore.EnqueueResult result = mediaGroupStore.enqueue(message, waitDuration.toMillis() / 1000.0);
        jobSchedulerService.scheduleMediaGroupFlush(result.key(), result.deadlineAt(), waitDuration);
        return TelegramWebhookHandler.DEFERRED;
    }

    private String buildStatusMessage(String chatId) {
        SessionSnapshot snapshot = sessionService.snapshot(chatId);
        return String.join("\n",
            "Bot 狀態：OK 🤖",
            "Session 狀態：" + (snapshot.active() ? "已生效 ✅" : "未生效 ❌"),
            "支援：文字、圖片、多圖、圖片 document、PDF、txt/md/html/json/csv、docx/xlsx"
        );
    }

    private String buildSessionMessage(String chatId) {
        SessionSnapshot snapshot = sessionService.snapshot(chatId);
        if (!snapshot.active()) {
            return "目前未有已生效 session。你可以直接 send 訊息開始，或者之後用 /summary 壓縮長對話。";
        }
        return String.join("\n",
            "目前 session：已生效",
            "訊息數：" + snapshot.messageCount(),
            "大概輪數：" + snapshot.turnCount(),
            "最後更新：" + snapshot.lastUpdatedAt(),
            "想壓縮 context 可以用 /summary。"
        );
    }

    private String buildMemoryMessage(String chatId) {
        MemorySnapshot snapshot = memoryService.snapshot(chatId);
        if (!snapshot.active()) {
            return "目前未有長期記憶。之後我會自動記低穩定偏好同持續背景；想清除可以打 /forget。";
        }
        return String.join("\n",
            "長期記憶：已生效",
            "最後更新：" + snapshot.lastUpdatedAt(),
            "",
            snapshot.memoryText(),
            "",
            "想清除可以用 /forget。"
        );
    }

    @SuppressWarnings("unchecked")
    private String extractChatId(Map<String, Object> update) {
        Object message = update.get("message");
        if (!(message instanceof Map<?, ?> messageMap)) {
            return null;
        }
        Object chat = ((Map<String, Object>) messageMap).get("chat");
        if (!(chat instanceof Map<?, ?> chatMap)) {
            return null;
        }
        Object chatId = ((Map<String, Object>) chatMap).get("id");
        return chatId == null ? null : String.valueOf(chatId);
    }
}
