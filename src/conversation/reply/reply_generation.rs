use std::sync::Arc;

use anyhow::Result;
use tracing::warn;

use crate::codex::memory::codex_memory_client::CodexMemoryClient;
use crate::codex::reply::codex_reply_client::CodexReplyClient;
use crate::conversation::reply::attachment_downloader::AttachmentDownloader;
use crate::conversation::reply::processed_update_service::ProcessedUpdateService;
use crate::conversation::reply::reply_result::ReplyResult;
use crate::conversation::session::session_service::SessionService;
use crate::conversation::storage::chat_memory_repository::ChatMemoryRepository;
use crate::conversation::storage::chat_session_repository::ChatSessionRepository;
use crate::telegram::api::typing_status::TypingStatus;
use crate::telegram::shared::inbound_message::InboundMessage;
use crate::telegram::shared::telegram_types::TelegramGateway;

pub struct ReplyGenerationService {
    reply_client: Arc<CodexReplyClient>,
    chat_session_repository: Arc<ChatSessionRepository>,
    chat_memory_repository: Arc<ChatMemoryRepository>,
    memory_client: Arc<CodexMemoryClient>,
    processed_update_service: Arc<ProcessedUpdateService>,
    session_service: Arc<SessionService>,
    telegram_client: Arc<dyn TelegramGateway>,
    attachment_downloader: Arc<AttachmentDownloader>,
}

impl ReplyGenerationService {
    #[allow(clippy::too_many_arguments)]
    pub fn new(
        reply_client: Arc<CodexReplyClient>,
        chat_session_repository: Arc<ChatSessionRepository>,
        chat_memory_repository: Arc<ChatMemoryRepository>,
        memory_client: Arc<CodexMemoryClient>,
        processed_update_service: Arc<ProcessedUpdateService>,
        session_service: Arc<SessionService>,
        telegram_client: Arc<dyn TelegramGateway>,
        attachment_downloader: Arc<AttachmentDownloader>,
    ) -> Self {
        Self {
            reply_client,
            chat_session_repository,
            chat_memory_repository,
            memory_client,
            processed_update_service,
            session_service,
            telegram_client,
            attachment_downloader,
        }
    }

    pub async fn handle(&self, message: &InboundMessage) -> Result<()> {
        match self.handle_inner(message).await {
            Ok(()) => Ok(()),
            Err(error) => {
                // Releasing the claim lets Telegram's retry regenerate the reply.
                self.processed_update_service.clear_processing(message.update_id).await?;
                Err(error)
            }
        }
    }

    async fn handle_inner(&self, message: &InboundMessage) -> Result<()> {
        self.processed_update_service.prune_if_needed().await?;
        let reply = TypingStatus::with_typing_status(self.telegram_client.clone(), &message.chat_id, self.generate_reply(message)).await?;
        self.deliver_reply(message, &reply).await
    }

    async fn generate_reply(&self, message: &InboundMessage) -> Result<ReplyResult> {
        let image_file_paths = self.attachment_downloader.download_images(message.effective_image_file_ids()).await?;
        let result = self
            .reply_client
            .generate_reply(
                Some(message.text_or_empty()),
                self.find_last_response_id(&message.chat_id).await?.as_deref(),
                &image_file_paths,
                message.reply_to_text.as_deref(),
                self.find_memory_text(&message.chat_id).await?.as_deref(),
            )
            .await;
        self.attachment_downloader.cleanup(&image_file_paths).await;
        result
    }

    async fn deliver_reply(&self, message: &InboundMessage, reply: &ReplyResult) -> Result<()> {
        self.processed_update_service.save_pending_reply(message.update_id, &message.chat_id, message.message_id, reply).await?;
        self.telegram_client.send_message(&message.chat_id, Some(&reply.text), &reply.suggested_replies, false).await?;
        self.session_service.persist_conversation_state(&message.chat_id, reply.conversation_state.as_deref()).await?;
        self.refresh_memory(&message.chat_id, message.text.as_deref(), &reply.text).await;
        self.processed_update_service.mark_processed(message.update_id, &message.chat_id, message.message_id).await
    }

    async fn find_last_response_id(&self, chat_id: &str) -> Result<Option<String>> {
        Ok(self.chat_session_repository.find_active(chat_id).await?.and_then(|session| session.last_response_id))
    }

    async fn find_memory_text(&self, chat_id: &str) -> Result<Option<String>> {
        Ok(self.chat_memory_repository.find(chat_id).await?.and_then(|memory| memory.memory_text))
    }

    /// Memory refresh is best effort: a failure must not lose the reply that
    /// was already delivered.
    async fn refresh_memory(&self, chat_id: &str, user_message: Option<&str>, assistant_reply: &str) {
        let Some(user_message) = user_message.filter(|user_message| !user_message.trim().is_empty()) else {
            return;
        };
        if let Err(error) = self.refresh_memory_inner(chat_id, user_message, assistant_reply).await {
            warn!("Failed to refresh long-term memory chat_id={chat_id} error={error}");
        }
    }

    async fn refresh_memory_inner(&self, chat_id: &str, user_message: &str, assistant_reply: &str) -> Result<()> {
        let existing_memory = self.chat_memory_repository.find(chat_id).await?.and_then(|memory| memory.memory_text).unwrap_or_default();
        let merged_memory = self.memory_client.merge(Some(&existing_memory), Some(user_message), Some(assistant_reply)).await?;
        if merged_memory != existing_memory {
            self.chat_memory_repository.persist(chat_id, Some(&merged_memory)).await?;
        }
        Ok(())
    }
}
