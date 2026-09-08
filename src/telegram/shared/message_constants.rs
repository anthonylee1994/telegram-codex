use std::sync::LazyLock;

pub static HELP_MESSAGE: LazyLock<String> = LazyLock::new(|| {
    [
        "可用 command：",
        "/help - 顯示可用指令",
        "/status - 睇 bot 狀態",
        "/session - 睇目前 session 狀態",
        "/memory - 睇長期記憶",
        "/forget - 清除長期記憶",
        "/compact - 將長對話壓縮成新 context",
        "/new - 開新 session",
        "",
        "你亦可以直接講「記住...」、「將長期記憶改成...」、「忘記...」嚟管理長期記憶。",
        "",
        "我而家僅支持文字、圖片。",
    ]
    .join("\n")
});

pub static START_MESSAGE: LazyLock<String> = LazyLock::new(|| {
    [
        "您好，我係您嘅 AI 助手。",
        "",
        "直接 send 文字或者圖片畀我就得。",
        "想睇指令就打 /help。",
        "想重新開過個 session，就打 /new。",
    ]
    .join("\n")
});

pub const NEW_SESSION_MESSAGE: &str = "已經開咗個新 session，你可以重新開始。";
pub const RATE_LIMIT_MESSAGE: &str = "你打得太快，等一陣再試。";
pub const RESET_MEMORY_MESSAGE: &str = "已經刪除長期記憶。";
pub const COMPACT_QUEUED_MESSAGE: &str = "開始 compact 目前 session。整完之後我會再主動 send 結果畀你。";
pub const COMPACT_BASELINE_MESSAGE: &str = "以下係之前對話 compact 後嘅內容。之後請按呢份內容延續對話上下文。";
pub const TOO_MANY_IMAGES_MESSAGE: &str = "你一次過畀太多圖，我未必可以準確逐張睇。揀最多 10 張最關鍵嘅圖，或者直接講明想我集中比較邊幾張、邊一方面。";
pub const UNAUTHORIZED_MESSAGE: &str = "呢個 bot 暫時只限指定用戶使用。";
pub const UNSUPPORTED_MESSAGE: &str = "你輸入嘅內容，我仲未識得處理。";
pub const REPLY_TO_IMAGE: &str = "用戶引用咗一張相。";
pub const REPLY_TO_IMAGE_DOCUMENT: &str = "用戶引用咗一個圖片檔案。";

pub const DEFAULT_SUGGESTED_REPLIES: &[&str] = &["可唔可以講詳細啲？", "幫我列重點。", "下一步可以點做？"];

pub fn default_suggested_replies() -> Vec<String> {
    DEFAULT_SUGGESTED_REPLIES.iter().map(|reply| (*reply).to_owned()).collect()
}
