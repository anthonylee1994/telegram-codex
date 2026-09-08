use anyhow::Result;
use sqlx::Row;
use tracing::info;

use crate::clock::now_ms;
use crate::database::Database;

#[derive(Clone, Debug)]
pub struct ChatMemoryRecord {
    pub chat_id: String,
    pub memory_text: Option<String>,
    pub updated_at: i64,
}

pub struct ChatMemoryRepository {
    database: Database,
}

impl ChatMemoryRepository {
    pub fn new(database: Database) -> Self {
        Self { database }
    }

    pub async fn find(&self, chat_id: &str) -> Result<Option<ChatMemoryRecord>> {
        let row = sqlx::query("select chat_id, memory_text, updated_at from chat_memories where chat_id = ?")
            .bind(chat_id)
            .fetch_optional(&self.database)
            .await?;
        match row {
            Some(row) => Ok(Some(ChatMemoryRecord {
                chat_id: row.try_get("chat_id")?,
                memory_text: row.try_get("memory_text")?,
                updated_at: row.try_get("updated_at")?,
            })),
            None => Ok(None),
        }
    }

    pub async fn persist(&self, chat_id: &str, memory_text: Option<&str>) -> Result<()> {
        let normalized = memory_text.unwrap_or("").trim().to_owned();
        if normalized.is_empty() {
            return self.reset(chat_id).await;
        }
        sqlx::query(
            "insert into chat_memories (chat_id, memory_text, updated_at)
             values (?, ?, ?)
             on conflict(chat_id) do update set
               memory_text = excluded.memory_text,
               updated_at = excluded.updated_at",
        )
        .bind(chat_id)
        .bind(&normalized)
        .bind(now_ms())
        .execute(&self.database)
        .await?;
        Ok(())
    }

    pub async fn reset(&self, chat_id: &str) -> Result<()> {
        sqlx::query("delete from chat_memories where chat_id = ?").bind(chat_id).execute(&self.database).await?;
        info!("Reset chat memory chat_id={chat_id}");
        Ok(())
    }
}
