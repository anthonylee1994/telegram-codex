use std::sync::Arc;

use anyhow::Result;

use crate::conversation::reply::processed_update_service::ProcessedUpdateService;
use crate::conversation::session::session_service::SessionCompactResult;
use crate::telegram::commands::compact_result_sender::CompactResultSender;
use crate::telegram::shared::inbound_message::InboundMessage;
use crate::telegram::shared::telegram_types::TelegramGateway;

pub struct TelegramCommandResponder {
    processed_update_service: Arc<ProcessedUpdateService>,
    telegram_client: Arc<dyn TelegramGateway>,
    compact_result_sender: Arc<CompactResultSender>,
}

impl TelegramCommandResponder {
    pub fn new(processed_update_service: Arc<ProcessedUpdateService>, telegram_client: Arc<dyn TelegramGateway>, compact_result_sender: Arc<CompactResultSender>) -> Self {
        Self {
            processed_update_service,
            telegram_client,
            compact_result_sender,
        }
    }

    pub async fn reply(&self, message: &InboundMessage, text: &str) -> Result<()> {
        self.telegram_client.send_message(&message.chat_id, Some(text), &[], true).await?;
        self.processed_update_service.mark_processed_message(message).await
    }

    pub async fn send_compact_result(&self, message: &InboundMessage, result: &SessionCompactResult) -> Result<()> {
        self.compact_result_sender.send(&message.chat_id, result).await?;
        self.processed_update_service.mark_processed_message(message).await
    }
}
