use std::sync::Arc;

use anyhow::Result;
use tracing::{info, warn};

use crate::config::AppConfig;
use crate::conversation::reply::chat_rate_limiter::ChatRateLimiter;
use crate::conversation::reply::processed_update_service::ProcessedUpdateService;
use crate::telegram::shared::inbound_message::InboundMessage;
use crate::telegram::shared::message_constants;
use crate::telegram::shared::telegram_types::TelegramGateway;

pub struct ReplyRequestGuard {
    config: Arc<AppConfig>,
    rate_limiter: Arc<ChatRateLimiter>,
    processed_update_service: Arc<ProcessedUpdateService>,
    telegram_client: Arc<dyn TelegramGateway>,
}

impl ReplyRequestGuard {
    pub fn new(config: Arc<AppConfig>, rate_limiter: Arc<ChatRateLimiter>, processed_update_service: Arc<ProcessedUpdateService>, telegram_client: Arc<dyn TelegramGateway>) -> Self {
        Self {
            config,
            rate_limiter,
            processed_update_service,
            telegram_client,
        }
    }

    pub async fn allow(&self, message: &InboundMessage) -> Result<bool> {
        Ok(self.allow_authorized_user(message).await? && self.allow_supported_media_group_size(message).await? && self.allow_chat_rate(message).await? && self.begin_processing(message).await?)
    }

    async fn send_and_mark_processed(&self, message: &InboundMessage, text: &str) -> Result<()> {
        self.telegram_client.send_message(&message.chat_id, Some(text), &[], false).await?;
        self.processed_update_service.mark_processed_message(message).await
    }

    async fn allow_authorized_user(&self, message: &InboundMessage) -> Result<bool> {
        if self.config.allowed_telegram_user_ids.is_empty() || self.config.allowed_telegram_user_ids.contains(&message.user_id) {
            return Ok(true);
        }
        warn!("Rejected unauthorized Telegram user chat_id={} user_id={}", message.chat_id, message.user_id);
        self.send_and_mark_processed(message, message_constants::UNAUTHORIZED_MESSAGE).await?;
        Ok(false)
    }

    async fn allow_supported_media_group_size(&self, message: &InboundMessage) -> Result<bool> {
        if !message.media_group() || message.image_count() <= self.config.max_media_group_images {
            return Ok(true);
        }
        self.send_and_mark_processed(message, message_constants::TOO_MANY_IMAGES_MESSAGE).await?;
        Ok(false)
    }

    async fn allow_chat_rate(&self, message: &InboundMessage) -> Result<bool> {
        if self.rate_limiter.allow(&message.chat_id) {
            return Ok(true);
        }
        self.send_and_mark_processed(message, message_constants::RATE_LIMIT_MESSAGE).await?;
        Ok(false)
    }

    async fn begin_processing(&self, message: &InboundMessage) -> Result<bool> {
        if self.processed_update_service.begin_processing(message).await? {
            return Ok(true);
        }
        info!("Ignored duplicate update update_id={}", message.update_id);
        Ok(false)
    }
}
