use std::sync::{Arc, Mutex, OnceLock, Weak};
use std::time::Duration;

use async_trait::async_trait;
use tokio::task::JoinHandle;
use tracing::error;

use crate::conversation::reply::reply_generation::ReplyGenerationService;
use crate::conversation::session::session_service::SessionService;
use crate::conversation::storage::media_group_buffer_repository::{FlushResult, MediaGroupBufferRepository};
use crate::telegram::commands::compact_result_sender::CompactResultSender;
use crate::telegram::shared::inbound_message::InboundMessage;

/// Breaks the scheduler <-> inbound processor cycle that NestJS solved with
/// `forwardRef`.
#[async_trait]
pub trait InboundMessageProcessorPort: Send + Sync {
    async fn process(&self, message: Option<InboundMessage>) -> anyhow::Result<()>;
}

/// Fire-and-forget background work: reply generation, album flushes, and
/// session compaction.
pub struct JobScheduler {
    media_group_store: Arc<MediaGroupBufferRepository>,
    reply_generation_service: Arc<ReplyGenerationService>,
    session_service: Arc<SessionService>,
    compact_result_sender: Arc<CompactResultSender>,
    inbound_message_processor: OnceLock<Weak<dyn InboundMessageProcessorPort>>,
    tasks: Mutex<Vec<JoinHandle<()>>>,
}

impl JobScheduler {
    pub fn new(
        media_group_store: Arc<MediaGroupBufferRepository>,
        reply_generation_service: Arc<ReplyGenerationService>,
        session_service: Arc<SessionService>,
        compact_result_sender: Arc<CompactResultSender>,
    ) -> Self {
        Self {
            media_group_store,
            reply_generation_service,
            session_service,
            compact_result_sender,
            inbound_message_processor: OnceLock::new(),
            tasks: Mutex::new(Vec::new()),
        }
    }

    pub fn set_inbound_message_processor(&self, processor: Weak<dyn InboundMessageProcessorPort>) {
        let _ = self.inbound_message_processor.set(processor);
    }

    pub fn enqueue_reply_generation(self: &Arc<Self>, message: InboundMessage) {
        let scheduler = self.clone();
        self.track(tokio::spawn(async move {
            let update_id = message.update_id;
            if let Err(error) = scheduler.reply_generation_service.handle(&message).await {
                error!("Reply generation failed update_id={update_id}: {error:?}");
            }
        }));
    }

    pub fn schedule_media_group_flush(self: &Arc<Self>, key: String, expected_deadline_at: i64, wait_duration_ms: i64) {
        let scheduler = self.clone();
        self.track(tokio::spawn(async move {
            tokio::time::sleep(Duration::from_millis(wait_duration_ms.max(0) as u64)).await;
            if let Err(error) = scheduler.flush_media_group(&key, expected_deadline_at).await {
                error!("Media group flush failed key={key}: {error:?}");
            }
        }));
    }

    pub fn enqueue_session_compact(self: &Arc<Self>, chat_id: String) {
        let scheduler = self.clone();
        self.track(tokio::spawn(async move {
            if let Err(error) = scheduler.run_session_compact(&chat_id).await {
                error!("Session compact failed chat_id={chat_id}: {error:?}");
            }
        }));
    }

    pub fn shutdown(&self) {
        let mut tasks = self.tasks.lock().expect("scheduler mutex poisoned");
        for task in tasks.drain(..) {
            task.abort();
        }
    }

    async fn run_session_compact(&self, chat_id: &str) -> anyhow::Result<()> {
        let result = self.session_service.compact(chat_id).await?;
        self.compact_result_sender.send(chat_id, &result).await
    }

    async fn flush_media_group(self: &Arc<Self>, key: &str, expected_deadline_at: i64) -> anyhow::Result<()> {
        match self.media_group_store.flush(key, expected_deadline_at).await? {
            FlushResult::Ready { message } => {
                if let Some(processor) = self.inbound_message_processor.get().and_then(Weak::upgrade) {
                    processor.process(Some(message)).await?;
                }
            }
            FlushResult::Pending { wait_duration_seconds } => {
                self.schedule_media_group_flush(key.to_owned(), expected_deadline_at, (wait_duration_seconds * 1000.0).round() as i64);
            }
            FlushResult::Missing | FlushResult::Stale => {}
        }
        Ok(())
    }

    /// Keeps handles around so `shutdown` can cancel everything still pending,
    /// while dropping the ones that already finished.
    fn track(&self, handle: JoinHandle<()>) {
        let mut tasks = self.tasks.lock().expect("scheduler mutex poisoned");
        tasks.retain(|task| !task.is_finished());
        tasks.push(handle);
    }
}
