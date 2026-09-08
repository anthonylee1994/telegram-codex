use std::sync::Arc;
use std::sync::atomic::{AtomicI64, Ordering};

use anyhow::Result;
use tracing::info;

use crate::clock::now_ms;
use crate::conversation::conversation_constants::{PROCESSED_UPDATE_PRUNE_INTERVAL_MS, PROCESSED_UPDATE_RETENTION_MS};
use crate::conversation::reply::reply_result::ReplyResult;
use crate::conversation::session::session_service::SessionService;
use crate::conversation::storage::processed_update_repository::{ProcessedUpdateRecord, ProcessedUpdateRepository};
use crate::telegram::shared::inbound_message::InboundMessage;
use crate::telegram::shared::telegram_types::TelegramGateway;

pub struct ProcessedUpdateService {
    processed_update_repository: Arc<ProcessedUpdateRepository>,
    session_service: Arc<SessionService>,
    last_processed_update_prune_at: AtomicI64,
}

impl ProcessedUpdateService {
    pub fn new(processed_update_repository: Arc<ProcessedUpdateRepository>, session_service: Arc<SessionService>) -> Self {
        Self {
            processed_update_repository,
            session_service,
            last_processed_update_prune_at: AtomicI64::new(0),
        }
    }

    pub async fn find(&self, update_id: i64) -> Result<Option<ProcessedUpdateRecord>> {
        self.processed_update_repository.find(update_id).await
    }

    /// Claims every update that belongs to this message, rolling back the
    /// already claimed ones when one of them is taken.
    pub async fn begin_processing(&self, message: &InboundMessage) -> Result<bool> {
        let mut claimed_update_ids: Vec<i64> = Vec::new();
        for processing_update in &message.processing_updates {
            let claimed = self
                .processed_update_repository
                .begin_processing(processing_update.update_id, &message.chat_id, processing_update.message_id)
                .await?;
            if !claimed {
                for update_id in claimed_update_ids {
                    self.clear_processing(update_id).await?;
                }
                return Ok(false);
            }
            claimed_update_ids.push(processing_update.update_id);
        }
        Ok(true)
    }

    pub async fn clear_processing(&self, update_id: i64) -> Result<()> {
        self.processed_update_repository.clear_processing(update_id).await
    }

    pub fn duplicate(&self, processed_update: Option<&ProcessedUpdateRecord>) -> bool {
        processed_update.is_some_and(|processed_update| processed_update.sent_at.is_some())
    }

    pub fn replayable(&self, processed_update: Option<&ProcessedUpdateRecord>) -> bool {
        processed_update.is_some_and(|processed_update| {
            processed_update.reply_text.as_ref().is_some_and(|reply_text| !reply_text.is_empty())
                && processed_update.conversation_state.as_ref().is_some_and(|conversation_state| !conversation_state.is_empty())
        })
    }

    pub async fn resend_pending_reply(&self, message: &InboundMessage, processed_update: &ProcessedUpdateRecord, telegram_client: &dyn TelegramGateway) -> Result<()> {
        telegram_client
            .send_message(
                &message.chat_id,
                processed_update.reply_text.as_deref(),
                &self.parse_stored_suggested_replies(processed_update.suggested_replies.as_deref()),
                false,
            )
            .await?;
        self.session_service
            .persist_conversation_state(&message.chat_id, processed_update.conversation_state.as_deref())
            .await?;
        self.mark_processed_message(message).await
    }

    pub async fn mark_processed(&self, update_id: i64, chat_id: &str, message_id: i64) -> Result<()> {
        self.processed_update_repository.mark_processed(update_id, chat_id, message_id).await
    }

    pub async fn mark_processed_message(&self, message: &InboundMessage) -> Result<()> {
        for processing_update in &message.processing_updates {
            self.processed_update_repository
                .mark_processed(processing_update.update_id, &message.chat_id, processing_update.message_id)
                .await?;
        }
        Ok(())
    }

    pub async fn save_pending_reply(&self, update_id: i64, chat_id: &str, message_id: i64, result: &ReplyResult) -> Result<()> {
        self.processed_update_repository.save_pending_reply(update_id, chat_id, message_id, result).await
    }

    pub async fn prune_if_needed(&self) -> Result<()> {
        let now = now_ms();
        let last_prune_at = self.last_processed_update_prune_at.load(Ordering::Relaxed);
        if last_prune_at != 0 && now - last_prune_at < PROCESSED_UPDATE_PRUNE_INTERVAL_MS {
            return Ok(());
        }
        let cutoff = now - PROCESSED_UPDATE_RETENTION_MS;
        let deleted_count = self.processed_update_repository.prune_sent_before(cutoff).await?;
        self.last_processed_update_prune_at.store(now, Ordering::Relaxed);
        info!("Pruned processed updates count={deleted_count} cutoff={cutoff}");
        Ok(())
    }

    pub fn parse_stored_suggested_replies(&self, raw_suggested_replies: Option<&str>) -> Vec<String> {
        let Some(raw_suggested_replies) = raw_suggested_replies.filter(|value| !value.trim().is_empty()) else {
            return Vec::new();
        };
        match serde_json::from_str::<Vec<serde_json::Value>>(raw_suggested_replies) {
            Ok(replies) => replies
                .iter()
                .filter_map(|reply| reply.as_str())
                .filter(|reply| !reply.trim().is_empty())
                .map(|reply| reply.trim().to_owned())
                .collect(),
            Err(_) => Vec::new(),
        }
    }
}
