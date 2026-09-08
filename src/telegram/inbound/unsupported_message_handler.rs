use std::sync::Arc;

use anyhow::Result;

use crate::telegram::shared::inbound_message::InboundMessage;
use crate::telegram::shared::message_constants;
use crate::telegram::shared::telegram_types::{TelegramGateway, TelegramUpdate};

pub struct UnsupportedMessageHandler {
    telegram_client: Arc<dyn TelegramGateway>,
}

impl UnsupportedMessageHandler {
    pub fn new(telegram_client: Arc<dyn TelegramGateway>) -> Self {
        Self { telegram_client }
    }

    /// Returns `true` once the "I cannot handle this" notice has been sent, or
    /// when there was no chat to reply to at all.
    pub async fn handle(&self, message: Option<&InboundMessage>, update: Option<&TelegramUpdate>) -> Result<bool> {
        if let Some(message) = message
            && !message.unsupported()
        {
            return Ok(false);
        }
        let chat_id = message.map(|message| message.chat_id.clone()).or_else(|| {
            update
                .and_then(|update| update.message.as_ref())
                .and_then(|message| message.chat.as_ref())
                .and_then(|chat| chat.id.as_ref())
                .map(|id| id.as_string())
        });
        if let Some(chat_id) = chat_id.filter(|chat_id| !chat_id.is_empty()) {
            self.telegram_client.send_message(&chat_id, Some(message_constants::UNSUPPORTED_MESSAGE), &[], false).await?;
        }
        Ok(true)
    }
}
