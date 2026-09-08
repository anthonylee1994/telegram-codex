use std::sync::Arc;

use anyhow::Result;

use crate::conversation::conversation_constants::MIN_TRANSCRIPT_SIZE_FOR_COMPACT;
use crate::conversation::scheduler::job_scheduler::JobScheduler;
use crate::conversation::session::session_service::{SessionCompactResult, SessionService, SessionSnapshot};
use crate::telegram::commands::telegram_command_responder::TelegramCommandResponder;
use crate::telegram::shared::inbound_message::InboundMessage;
use crate::telegram::shared::message_constants;

pub struct CompactCommandExecutor {
    session_service: Arc<SessionService>,
    job_scheduler: Arc<JobScheduler>,
    responder: Arc<TelegramCommandResponder>,
}

impl CompactCommandExecutor {
    pub fn new(session_service: Arc<SessionService>, job_scheduler: Arc<JobScheduler>, responder: Arc<TelegramCommandResponder>) -> Self {
        Self {
            session_service,
            job_scheduler,
            responder,
        }
    }

    /// Answers immediately when compaction is pointless, otherwise the real work
    /// happens off the webhook request.
    pub async fn execute(&self, message: &InboundMessage) -> Result<()> {
        let snapshot = self.session_service.snapshot(&message.chat_id).await?;
        if let Some(immediate_result) = validate(&snapshot) {
            return self.responder.send_compact_result(message, &immediate_result).await;
        }
        self.job_scheduler.enqueue_session_compact(message.chat_id.clone());
        self.responder.reply(message, message_constants::COMPACT_QUEUED_MESSAGE).await
    }
}

fn validate(snapshot: &SessionSnapshot) -> Option<SessionCompactResult> {
    if !snapshot.active {
        return Some(SessionCompactResult::missing_session());
    }
    if snapshot.message_count < MIN_TRANSCRIPT_SIZE_FOR_COMPACT {
        return Some(SessionCompactResult::too_short(snapshot.message_count));
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::conversation::session::session_service::SessionCompactStatus;

    #[test]
    fn rejects_missing_and_short_sessions() {
        let missing = SessionSnapshot {
            active: false,
            message_count: 0,
            turn_count: 0,
            last_updated_at: None,
        };
        assert_eq!(validate(&missing).unwrap().status, SessionCompactStatus::MissingSession);

        let short = SessionSnapshot {
            active: true,
            message_count: 2,
            turn_count: 1,
            last_updated_at: None,
        };
        assert_eq!(validate(&short).unwrap().status, SessionCompactStatus::TooShort);

        let ready = SessionSnapshot {
            active: true,
            message_count: 4,
            turn_count: 2,
            last_updated_at: None,
        };
        assert!(validate(&ready).is_none());
    }
}
