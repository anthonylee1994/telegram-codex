use std::sync::Arc;

use anyhow::Result;

use crate::conversation::session::session_service::SessionService;

pub struct TelegramStatusMessageBuilder {
    session_service: Arc<SessionService>,
}

impl TelegramStatusMessageBuilder {
    pub fn new(session_service: Arc<SessionService>) -> Self {
        Self { session_service }
    }

    pub async fn build_status_message(&self, chat_id: &str) -> Result<String> {
        let snapshot = self.session_service.snapshot(chat_id).await?;
        let session_status = if snapshot.active { "已生效" } else { "未生效" };
        Ok(["Bot 狀態：OK 🤖".to_owned(), format!("Session 狀態：{session_status}"), "只支持：文字、圖片".to_owned()].join("\n"))
    }

    pub async fn build_session_message(&self, chat_id: &str) -> Result<String> {
        let snapshot = self.session_service.snapshot(chat_id).await?;
        if !snapshot.active {
            return Ok("目前未有已生效 session。你可以直接 send 訊息開始，或者之後打 /compact 壓縮長對話。".to_owned());
        }
        Ok([
            "目前 session：已生效".to_owned(),
            format!("訊息數：{}", snapshot.message_count),
            format!("大概輪數：{}", snapshot.turn_count),
            format!("最後更新：{}", snapshot.last_updated_at.unwrap_or_default()),
            "想壓縮 context 可以打 /compact。".to_owned(),
        ]
        .join("\n"))
    }

    pub async fn build_memory_message(&self, chat_id: &str) -> Result<String> {
        let snapshot = self.session_service.memory_snapshot(chat_id).await?;
        if !snapshot.active {
            return Ok("目前未有長期記憶。你可以直接叫我記住、改寫或者刪除長期記憶；我之後亦會自動記低穩定偏好同持續背景。想清除可以打 /forget。".to_owned());
        }
        Ok([
            "長期記憶：已生效".to_owned(),
            format!("最後更新：{}", snapshot.last_updated_at.unwrap_or_default()),
            String::new(),
            snapshot.memory_text.unwrap_or_default(),
            String::new(),
            "你可以直接叫我改寫或者刪除長期記憶。".to_owned(),
            "想清除可以打 /forget。".to_owned(),
        ]
        .join("\n"))
    }
}
