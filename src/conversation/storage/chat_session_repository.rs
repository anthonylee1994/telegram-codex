use std::sync::Arc;

use anyhow::Result;
use sqlx::Row;
use tracing::info;

use crate::clock::now_ms;
use crate::config::AppConfig;
use crate::database::Database;

#[derive(Clone, Debug)]
pub struct ChatSessionRecord {
    pub chat_id: String,
    pub last_response_id: Option<String>,
    pub updated_at: i64,
}

pub struct ChatSessionRepository {
    config: Arc<AppConfig>,
    database: Database,
}

impl ChatSessionRepository {
    pub fn new(config: Arc<AppConfig>, database: Database) -> Self {
        Self { config, database }
    }

    /// Returns the session unless it aged past `SESSION_TTL_DAYS`, in which case
    /// the row is dropped so the next message starts fresh.
    pub async fn find_active(&self, chat_id: &str) -> Result<Option<ChatSessionRecord>> {
        let row = sqlx::query("select chat_id, last_response_id, updated_at from chat_sessions where chat_id = ?")
            .bind(chat_id)
            .fetch_optional(&self.database)
            .await?;
        let Some(row) = row else {
            return Ok(None);
        };
        let record = ChatSessionRecord {
            chat_id: row.try_get("chat_id")?,
            last_response_id: row.try_get("last_response_id")?,
            updated_at: row.try_get("updated_at")?,
        };
        if now_ms() - record.updated_at > self.config.session_ttl_ms() {
            sqlx::query("delete from chat_sessions where chat_id = ?").bind(chat_id).execute(&self.database).await?;
            info!("Reset expired session chat_id={chat_id}");
            return Ok(None);
        }
        Ok(Some(record))
    }

    pub async fn persist(&self, chat_id: &str, conversation_state: Option<&str>) -> Result<()> {
        sqlx::query(
            "insert into chat_sessions (chat_id, last_response_id, updated_at)
             values (?, ?, ?)
             on conflict(chat_id) do update set
               last_response_id = excluded.last_response_id,
               updated_at = excluded.updated_at",
        )
        .bind(chat_id)
        .bind(conversation_state)
        .bind(now_ms())
        .execute(&self.database)
        .await?;
        Ok(())
    }

    pub async fn reset(&self, chat_id: &str) -> Result<()> {
        sqlx::query("delete from chat_sessions where chat_id = ?").bind(chat_id).execute(&self.database).await?;
        info!("Reset chat session chat_id={chat_id}");
        Ok(())
    }
}
