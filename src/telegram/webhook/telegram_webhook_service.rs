use std::sync::Arc;

use anyhow::Result;

use crate::telegram::inbound::inbound_message_processor::InboundMessageProcessor;
use crate::telegram::shared::inbound_message::InboundMessage;
use crate::telegram::shared::telegram_types::TelegramUpdate;
use crate::telegram::shared::telegram_update_parser::TelegramUpdateParser;

pub struct TelegramWebhookRouter {
    inbound_message_processor: Arc<InboundMessageProcessor>,
}

impl TelegramWebhookRouter {
    pub fn new(inbound_message_processor: Arc<InboundMessageProcessor>) -> Self {
        Self { inbound_message_processor }
    }

    pub async fn route(&self, message: Option<&InboundMessage>, update: Option<&TelegramUpdate>) -> Result<()> {
        if let Some(message) = message
            && message.media_group()
        {
            return self.inbound_message_processor.defer_media_group(message).await;
        }
        self.inbound_message_processor.process(message, update).await
    }
}

pub struct TelegramWebhookService {
    telegram_update_parser: Arc<TelegramUpdateParser>,
    webhook_router: Arc<TelegramWebhookRouter>,
}

impl TelegramWebhookService {
    pub fn new(telegram_update_parser: Arc<TelegramUpdateParser>, webhook_router: Arc<TelegramWebhookRouter>) -> Self {
        Self {
            telegram_update_parser,
            webhook_router,
        }
    }

    pub async fn handle(&self, update: Option<&TelegramUpdate>) -> Result<()> {
        let message = self.telegram_update_parser.parse_incoming_telegram_message(update);
        self.webhook_router.route(message.as_ref(), update).await
    }
}
