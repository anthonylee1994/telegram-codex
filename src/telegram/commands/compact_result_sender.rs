use std::sync::Arc;

use anyhow::Result;

use crate::conversation::session::session_service::{SessionCompactResult, SessionCompactStatus};
use crate::telegram::shared::telegram_types::TelegramGateway;

pub struct CompactResultSender {
    telegram_client: Arc<dyn TelegramGateway>,
}

impl CompactResultSender {
    pub fn new(telegram_client: Arc<dyn TelegramGateway>) -> Self {
        Self { telegram_client }
    }

    pub async fn send(&self, chat_id: &str, result: &SessionCompactResult) -> Result<()> {
        let text = match result.status {
            SessionCompactStatus::MissingSession => "而家冇 active session，冇嘢可以 compact。".to_owned(),
            SessionCompactStatus::TooShort => format!("目前對話得 {} 段訊息，未去到要壓縮 context。", result.message_count.unwrap_or(0)),
            SessionCompactStatus::Ok => [
                "已經將目前 session compact 成新 context。".to_owned(),
                format!("原本訊息：{}", result.original_message_count.unwrap_or(0)),
                String::new(),
                result.compact_text.clone().unwrap_or_default(),
            ]
            .join("\n"),
        };
        self.telegram_client.send_message(chat_id, Some(&text), &[], true).await
    }
}
