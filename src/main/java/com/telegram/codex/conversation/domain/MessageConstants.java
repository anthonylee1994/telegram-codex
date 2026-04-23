package com.telegram.codex.conversation.domain;

import java.util.List;

public final class MessageConstants {

    private MessageConstants() {
        // Constants class
    }

    public static final String HELP_MESSAGE = String.join("\n",
        "可用 command：",
        "/help - 顯示可用指令",
        "/status - 睇 bot 狀態",
        "/session - 睇目前 session 狀態",
        "/memory - 睇長期記憶",
        "/forget - 清除長期記憶",
        "/compact - 將長對話壓縮成新 context",
        "/new - 開新 session",
        "",
        "你亦可以直接講「記住...」、「將長期記憶改成...」、「忘記...」。",
        "",
        "我而家僅支持文字、圖片。"
    );

    public static final String NEW_SESSION_MESSAGE = "已經開咗個新 session，你可以重新開始。";
    public static final String RATE_LIMIT_MESSAGE = "你打得太快，等一陣再試。";
    public static final String RESET_MEMORY_MESSAGE = "已經刪除長期記憶。";

    public static final String COMPACT_QUEUED_MESSAGE = "開始 compact 目前 session。整完之後我會再主動 send 結果畀你。";
    public static final String COMPACT_BASELINE_MESSAGE = "以下係之前對話 compact 後嘅內容。之後請按呢份內容延續對話上下文。";

    public static final String START_MESSAGE = String.join("\n",
        "您好，我係您嘅 AI 助手。",
        "",
        "直接 send 文字或者圖片畀我就得。",
        "想睇指令就打 /help。",
        "想重新開過個 session，就打 /new。"
    );

    public static final String TOO_MANY_IMAGES_MESSAGE = "你一次過畀太多圖，我未必可以準確逐張睇。揀最多 10 張最關鍵嘅圖，或者直接講明想我集中比較邊幾張、邊一方面。";
    public static final String SENSITIVE_INTENT_MESSAGE = "我唔會主動檢查本機 codebase、repo、system prompt 或內部檔案。如果你想我 review 某段 code，直接貼內容出嚟。";
    public static final String UNAUTHORIZED_MESSAGE = "呢個 bot 暫時只限指定用戶使用。";
    public static final String UNSUPPORTED_MESSAGE = "你輸入嘅內容，我仲未識得處理。";

    public static final String REPLY_TO_IMAGE = "用戶引用咗一張相。";
    public static final String REPLY_TO_IMAGE_DOCUMENT = "用戶引用咗一個圖片檔案。";

    public static final List<String> DEFAULT_SUGGESTED_REPLIES = List.of(
        "可唔可以講詳細啲？",
        "幫我列重點。",
        "下一步可以點做？"
    );
}
