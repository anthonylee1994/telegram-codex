package com.telegramcodex.conversation.webhooks;

import com.telegramcodex.conversation.ConversationService;
import com.telegramcodex.conversation.MediaGroupStore;
import com.telegramcodex.conversation.ProcessedUpdateFlow;
import com.telegramcodex.jobs.JobSchedulerService;
import com.telegramcodex.telegram.InboundMessage;
import com.telegramcodex.telegram.TelegramClient;
import com.telegramcodex.telegram.TelegramWebhookHandler;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ActionExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActionExecutor.class);

    private final ConversationService conversationService;
    private final JobSchedulerService jobSchedulerService;
    private final MediaGroupStore mediaGroupStore;
    private final ProcessedUpdateFlow processedUpdateFlow;
    private final TelegramClient telegramClient;

    public ActionExecutor(
        ConversationService conversationService,
        JobSchedulerService jobSchedulerService,
        MediaGroupStore mediaGroupStore,
        ProcessedUpdateFlow processedUpdateFlow,
        TelegramClient telegramClient
    ) {
        this.conversationService = conversationService;
        this.jobSchedulerService = jobSchedulerService;
        this.mediaGroupStore = mediaGroupStore;
        this.processedUpdateFlow = processedUpdateFlow;
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
                conversationService.resetSession(message.chatId());
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
                conversationService.resetMemory(message.chatId());
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
        Map<String, Object> snapshot = conversationService.sessionSnapshot(chatId);
        return String.join("\n",
            "Bot 狀態：OK 🤖",
            "Session 狀態：" + (Boolean.TRUE.equals(snapshot.get("active")) ? "已生效 ✅" : "未生效 ❌"),
            "支援：文字、圖片、多圖、圖片 document、PDF、txt/md/html/json/csv、docx/xlsx"
        );
    }

    private String buildSessionMessage(String chatId) {
        Map<String, Object> snapshot = conversationService.sessionSnapshot(chatId);
        if (!Boolean.TRUE.equals(snapshot.get("active"))) {
            return "目前未有已生效 session。你可以直接 send 訊息開始，或者之後用 /summary 壓縮長對話。";
        }
        return String.join("\n",
            "目前 session：已生效",
            "訊息數：" + snapshot.get("message_count"),
            "大概輪數：" + snapshot.get("turn_count"),
            "最後更新：" + snapshot.get("last_updated_at"),
            "想壓縮 context 可以用 /summary。"
        );
    }

    private String buildMemoryMessage(String chatId) {
        Map<String, Object> snapshot = conversationService.memorySnapshot(chatId);
        if (!Boolean.TRUE.equals(snapshot.get("active"))) {
            return "目前未有長期記憶。之後我會自動記低穩定偏好同持續背景；想清除可以打 /forget。";
        }
        return String.join("\n",
            "長期記憶：已生效",
            "最後更新：" + snapshot.get("last_updated_at"),
            "",
            String.valueOf(snapshot.get("memory_text")),
            "",
            "想清除可以打 /forget。"
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
