package com.telegram.codex.constants;

import java.util.List;

public final class MessageConstants {

    private MessageConstants() {
        // Constants class
    }

    // System messages
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
    public static final String SUMMARY_BASELINE_MESSAGE = "以下係之前對話嘅摘要。之後請按呢份摘要延續對話上下文。";

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

    // Reply reference messages
    public static final String REPLY_TO_IMAGE = "用戶引用咗一張相。";
    public static final String REPLY_TO_IMAGE_DOCUMENT = "用戶引用咗一個圖片檔案。";
    public static final String REPLY_TO_PDF = "用戶引用咗一份 PDF。";
    public static final String REPLY_TO_TEXT_DOCUMENT = "用戶引用咗一份文字檔。";

    // Document processing messages
    public static final String PDF_UNAVAILABLE_MESSAGE = "而家未開到 PDF 轉圖工具，所以暫時睇唔到 PDF。你可以改為 send screenshot，或者等我開通 PDF 支援。";
    public static final String TEXT_DOCUMENT_UNAVAILABLE_MESSAGE = "而家未開到 Office / 文字檔抽取工具，所以暫時睇唔到份檔案內容。你可以改為貼文字、send PDF，或者等我開通完整支援。";

    // Default suggested replies
    public static final List<String> DEFAULT_SUGGESTED_REPLIES = List.of(
        "可唔可以講詳細啲？",
        "幫我列重點。",
        "下一步可以點做？"
    );
}
