use std::sync::Arc;

use anyhow::Result;

use crate::codex::session::codex_session_compact_client::CodexSessionCompactClient;
use crate::codex::shared::transcript::Transcript;
use crate::conversation::conversation_constants::{MIN_TRANSCRIPT_SIZE_FOR_COMPACT, format_conversation_time};
use crate::conversation::storage::chat_memory_repository::ChatMemoryRepository;
use crate::conversation::storage::chat_session_repository::ChatSessionRepository;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SessionSnapshot {
    pub active: bool,
    pub message_count: usize,
    pub turn_count: usize,
    pub last_updated_at: Option<String>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SessionCompactStatus {
    MissingSession,
    TooShort,
    Ok,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SessionCompactResult {
    pub status: SessionCompactStatus,
    pub message_count: Option<usize>,
    pub original_message_count: Option<usize>,
    pub compact_text: Option<String>,
}

impl SessionCompactResult {
    pub fn missing_session() -> Self {
        Self {
            status: SessionCompactStatus::MissingSession,
            message_count: None,
            original_message_count: None,
            compact_text: None,
        }
    }

    pub fn too_short(message_count: usize) -> Self {
        Self {
            status: SessionCompactStatus::TooShort,
            message_count: Some(message_count),
            original_message_count: None,
            compact_text: None,
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct MemorySnapshot {
    pub active: bool,
    pub memory_text: Option<String>,
    pub last_updated_at: Option<String>,
}

pub struct SessionService {
    chat_session_repository: Arc<ChatSessionRepository>,
    session_compact_client: Arc<CodexSessionCompactClient>,
    chat_memory_repository: Arc<ChatMemoryRepository>,
}

impl SessionService {
    pub fn new(chat_session_repository: Arc<ChatSessionRepository>, session_compact_client: Arc<CodexSessionCompactClient>, chat_memory_repository: Arc<ChatMemoryRepository>) -> Self {
        Self {
            chat_session_repository,
            session_compact_client,
            chat_memory_repository,
        }
    }

    pub async fn persist_conversation_state(&self, chat_id: &str, conversation_state: Option<&str>) -> Result<()> {
        self.chat_session_repository.persist(chat_id, conversation_state).await
    }

    pub async fn reset(&self, chat_id: &str) -> Result<()> {
        self.chat_session_repository.reset(chat_id).await
    }

    pub async fn snapshot(&self, chat_id: &str) -> Result<SessionSnapshot> {
        let Some(session) = self.chat_session_repository.find_active(chat_id).await? else {
            return Ok(SessionSnapshot {
                active: false,
                message_count: 0,
                turn_count: 0,
                last_updated_at: None,
            });
        };
        let transcript = Transcript::from_conversation_state(session.last_response_id.as_deref());
        Ok(SessionSnapshot {
            active: true,
            message_count: transcript.size(),
            turn_count: transcript.size().div_ceil(2),
            last_updated_at: Some(format_conversation_time(session.updated_at)),
        })
    }

    /// Replaces the transcript with a single compacted baseline turn.
    pub async fn compact(&self, chat_id: &str) -> Result<SessionCompactResult> {
        let Some(session) = self.chat_session_repository.find_active(chat_id).await? else {
            return Ok(SessionCompactResult::missing_session());
        };
        let transcript = Transcript::from_conversation_state(session.last_response_id.as_deref());
        if transcript.size() < MIN_TRANSCRIPT_SIZE_FOR_COMPACT {
            return Ok(SessionCompactResult::too_short(transcript.size()));
        }
        let compact_text = self.session_compact_client.compact(&transcript).await?;
        self.chat_session_repository
            .persist(chat_id, Some(&Transcript::compact_baseline(&compact_text).to_conversation_state()))
            .await?;
        Ok(SessionCompactResult {
            status: SessionCompactStatus::Ok,
            message_count: None,
            original_message_count: Some(transcript.size()),
            compact_text: Some(compact_text),
        })
    }

    pub async fn memory_snapshot(&self, chat_id: &str) -> Result<MemorySnapshot> {
        let memory = self.chat_memory_repository.find(chat_id).await?;
        let memory_text = memory.as_ref().and_then(|memory| memory.memory_text.clone()).filter(|memory_text| !memory_text.trim().is_empty());
        match (memory, memory_text) {
            (Some(memory), Some(memory_text)) => Ok(MemorySnapshot {
                active: true,
                memory_text: Some(memory_text),
                last_updated_at: Some(format_conversation_time(memory.updated_at)),
            }),
            _ => Ok(MemorySnapshot {
                active: false,
                memory_text: None,
                last_updated_at: None,
            }),
        }
    }

    pub async fn reset_memory(&self, chat_id: &str) -> Result<()> {
        self.chat_memory_repository.reset(chat_id).await
    }
}
