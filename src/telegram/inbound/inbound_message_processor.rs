use std::sync::Arc;

use anyhow::Result;
use async_trait::async_trait;

use crate::config::AppConfig;
use crate::conversation::scheduler::job_scheduler::{InboundMessageProcessorPort, JobScheduler};
use crate::conversation::storage::media_group_buffer_repository::MediaGroupBufferRepository;
use crate::telegram::commands::telegram_command_handler::TelegramCommandHandler;
use crate::telegram::inbound::duplicate_update_handler::DuplicateUpdateHandler;
use crate::telegram::inbound::reply_request_guard::ReplyRequestGuard;
use crate::telegram::inbound::unsupported_message_handler::UnsupportedMessageHandler;
use crate::telegram::shared::inbound_message::InboundMessage;
use crate::telegram::shared::telegram_types::TelegramUpdate;

pub struct InboundMessageProcessor {
    unsupported_message_handler: Arc<UnsupportedMessageHandler>,
    duplicate_update_handler: Arc<DuplicateUpdateHandler>,
    telegram_command_handler: Arc<TelegramCommandHandler>,
    reply_request_guard: Arc<ReplyRequestGuard>,
    media_group_store: Arc<MediaGroupBufferRepository>,
    job_scheduler: Arc<JobScheduler>,
    config: Arc<AppConfig>,
}

impl InboundMessageProcessor {
    #[allow(clippy::too_many_arguments)]
    pub fn new(
        unsupported_message_handler: Arc<UnsupportedMessageHandler>,
        duplicate_update_handler: Arc<DuplicateUpdateHandler>,
        telegram_command_handler: Arc<TelegramCommandHandler>,
        reply_request_guard: Arc<ReplyRequestGuard>,
        media_group_store: Arc<MediaGroupBufferRepository>,
        job_scheduler: Arc<JobScheduler>,
        config: Arc<AppConfig>,
    ) -> Self {
        Self {
            unsupported_message_handler,
            duplicate_update_handler,
            telegram_command_handler,
            reply_request_guard,
            media_group_store,
            job_scheduler,
            config,
        }
    }

    /// Runs the inbound pipeline: unsupported -> duplicate -> command -> guard,
    /// and only then queues an actual reply.
    pub async fn process(&self, message: Option<&InboundMessage>, update: Option<&TelegramUpdate>) -> Result<()> {
        let Some(message) = message else {
            self.unsupported_message_handler.handle(None, update).await?;
            return Ok(());
        };
        if self.unsupported_message_handler.handle(Some(message), update).await? {
            return Ok(());
        }
        if self.duplicate_update_handler.handle(message).await? {
            return Ok(());
        }
        if self.telegram_command_handler.handle(message).await? {
            return Ok(());
        }
        if !self.reply_request_guard.allow(message).await? {
            return Ok(());
        }
        self.job_scheduler.enqueue_reply_generation(message.clone());
        Ok(())
    }

    /// Albums arrive as separate updates, so they are buffered until the group
    /// is complete.
    pub async fn defer_media_group(&self, message: &InboundMessage) -> Result<()> {
        let (result, key) = self.media_group_store.enqueue(message, self.config.media_group_wait_ms as f64 / 1000.0).await?;
        self.job_scheduler.schedule_media_group_flush(key, result.deadline_at, self.config.media_group_wait_ms);
        Ok(())
    }
}

#[async_trait]
impl InboundMessageProcessorPort for InboundMessageProcessor {
    async fn process(&self, message: Option<InboundMessage>) -> Result<()> {
        InboundMessageProcessor::process(self, message.as_ref(), None).await
    }
}
