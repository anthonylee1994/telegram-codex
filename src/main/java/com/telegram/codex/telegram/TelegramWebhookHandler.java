package com.telegram.codex.telegram;

import com.telegram.codex.config.AppProperties;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TelegramWebhookHandler {

    public static final Object DEFERRED = new Object();
    public static final String GENERIC_ERROR_MESSAGE = "我要休息一陣，遲啲叫醒我。";
    public static final String HELP_MESSAGE = String.join("\n",
        "可用 command：",
        "/help - 顯示指令同支援範圍",
        "/status - 睇 bot 狀態",
        "/session - 睇目前 session 狀態",
        "/memory - 睇長期記憶",
        "/forget - 清除長期記憶",
        "/summary - 將長對話壓縮成新 context",
        "/new - 開新 session",
        "",
        "我而家支援文字、圖片、多圖、圖片 document、PDF、txt/md/html/json/csv、docx/xlsx。"
    );
    public static final String NEW_SESSION_MESSAGE = "已經開咗個新 session，你可以重新開始。";
    public static final String RATE_LIMIT_MESSAGE = "你打得太快，等一陣再試。";
    public static final String RESET_MEMORY_MESSAGE = "已經刪除長期記憶。";
    public static final String SUMMARY_QUEUED_MESSAGE = "開始整理目前 session。整完之後我會再主動 send 摘要畀你。";
    public static final String START_MESSAGE = String.join("\n",
        "您好，我係您嘅 AI 助手。",
        "",
        "直接 send 文字或者圖片畀我就得。",
        "想睇指令就用 /help。",
        "想重新開過個 session，就用 /new。"
    );
    public static final String TOO_MANY_IMAGES_MESSAGE = "你一次過畀太多圖，我未必可以準確逐張睇。揀最多 10 張最關鍵嘅圖，或者直接講明想我集中比較邊幾張、邊一方面。";
    public static final String UNAUTHORIZED_MESSAGE = "呢個 bot 暫時只限指定用戶使用。";
    public static final String UNSUPPORTED_MESSAGE = "你輸入嘅內容，我仲未識得處理。";

    private final AppProperties properties;
    private final InboundMessageProcessor inboundMessageProcessor;
    private final TelegramUpdateParser telegramUpdateParser;

    public TelegramWebhookHandler(
        AppProperties properties,
        InboundMessageProcessor inboundMessageProcessor,
        TelegramUpdateParser telegramUpdateParser
    ) {
        this.properties = properties;
        this.inboundMessageProcessor = inboundMessageProcessor;
        this.telegramUpdateParser = telegramUpdateParser;
    }

    public void handle(Map<String, Object> update) {
        InboundMessage message = telegramUpdateParser.parseIncomingTelegramMessage(update);
        if (message != null && message.mediaGroup()) {
            Object deferred = inboundMessageProcessor.deferMediaGroup(message, Duration.ofMillis(properties.getMediaGroupWaitMs()));
            if (deferred == DEFERRED) {
                return;
            }
        }
        inboundMessageProcessor.process(message, update);
    }
}
