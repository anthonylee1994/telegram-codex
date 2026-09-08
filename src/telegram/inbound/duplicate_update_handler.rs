use std::sync::Arc;

use anyhow::Result;
use tracing::info;

use crate::conversation::reply::processed_update_service::ProcessedUpdateService;
use crate::telegram::shared::inbound_message::InboundMessage;
use crate::telegram::shared::telegram_types::TelegramGateway;

pub struct DuplicateUpdateHandler {
    processed_update_service: Arc<ProcessedUpdateService>,
    telegram_client: Arc<dyn TelegramGateway>,
}

impl DuplicateUpdateHandler {
    pub fn new(processed_update_service: Arc<ProcessedUpdateService>, telegram_client: Arc<dyn TelegramGateway>) -> Self {
        Self {
            processed_update_service,
            telegram_client,
        }
    }

    /// Returns `true` when the update was already handled, either by ignoring it
    /// or by resending the reply that was generated but never delivered.
    pub async fn handle(&self, message: &InboundMessage) -> Result<bool> {
        let processed_update = self.processed_update_service.find(message.update_id).await?;
        if self.processed_update_service.duplicate(processed_update.as_ref()) {
            info!("Ignored duplicate update update_id={}", message.update_id);
            return Ok(true);
        }
        if self.processed_update_service.replayable(processed_update.as_ref()) {
            let processed_update = processed_update.expect("replayable implies a stored record");
            self.processed_update_service.resend_pending_reply(message, &processed_update, self.telegram_client.as_ref()).await?;
            return Ok(true);
        }
        Ok(false)
    }
}
