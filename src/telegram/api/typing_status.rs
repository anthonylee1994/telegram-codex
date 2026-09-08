use std::sync::Arc;
use std::time::Duration;

use tokio::task::JoinHandle;
use tracing::debug;

use crate::telegram::shared::telegram_types::TelegramGateway;

const TYPING_REFRESH_INTERVAL: Duration = Duration::from_secs(4);

/// Keeps the Telegram "typing…" indicator alive while `action` runs.
pub struct TypingStatus;

impl TypingStatus {
    pub async fn with_typing_status<T, F>(telegram_client: Arc<dyn TelegramGateway>, chat_id: &str, action: F) -> T
    where
        F: Future<Output = T>,
    {
        if let Err(error) = telegram_client.send_chat_action(chat_id, "typing").await {
            debug!("Failed to send initial typing status for chat_id={chat_id}: {error}");
        }
        let _guard = TypingStatusGuard::start(telegram_client, chat_id.to_owned());
        action.await
    }
}

struct TypingStatusGuard {
    handle: JoinHandle<()>,
}

impl TypingStatusGuard {
    fn start(telegram_client: Arc<dyn TelegramGateway>, chat_id: String) -> Self {
        let handle = tokio::spawn(async move {
            let mut interval = tokio::time::interval(TYPING_REFRESH_INTERVAL);
            interval.tick().await;
            loop {
                interval.tick().await;
                if let Err(error) = telegram_client.send_chat_action(&chat_id, "typing").await {
                    debug!("Failed to send periodic typing status for chat_id={chat_id}: {error}");
                }
            }
        });
        Self { handle }
    }
}

impl Drop for TypingStatusGuard {
    fn drop(&mut self) {
        self.handle.abort();
    }
}
