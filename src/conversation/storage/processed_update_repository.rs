use anyhow::Result;
use sqlx::Row;

use crate::clock::now_ms;
use crate::conversation::reply::reply_result::ReplyResult;
use crate::database::Database;

const INFLIGHT_TIMEOUT_MS: i64 = 5 * 60 * 1000;

#[derive(Clone, Debug)]
pub struct ProcessedUpdateRecord {
    pub update_id: i64,
    pub chat_id: Option<String>,
    pub message_id: i64,
    pub processed_at: i64,
    pub reply_text: Option<String>,
    pub conversation_state: Option<String>,
    pub suggested_replies: Option<String>,
    pub sent_at: Option<i64>,
}

pub struct ProcessedUpdateRepository {
    database: Database,
}

impl ProcessedUpdateRepository {
    pub fn new(database: Database) -> Self {
        Self { database }
    }

    pub async fn find(&self, update_id: i64) -> Result<Option<ProcessedUpdateRecord>> {
        let row = sqlx::query(
            "select update_id, chat_id, message_id, processed_at, reply_text, conversation_state, suggested_replies, sent_at
             from processed_updates where update_id = ?",
        )
        .bind(update_id)
        .fetch_optional(&self.database)
        .await?;
        match row {
            Some(row) => Ok(Some(ProcessedUpdateRecord {
                update_id: row.try_get("update_id")?,
                chat_id: row.try_get("chat_id")?,
                message_id: row.try_get("message_id")?,
                processed_at: row.try_get("processed_at")?,
                reply_text: row.try_get("reply_text")?,
                conversation_state: row.try_get("conversation_state")?,
                suggested_replies: row.try_get("suggested_replies")?,
                sent_at: row.try_get("sent_at")?,
            })),
            None => Ok(None),
        }
    }

    /// Claims an update for processing. Returns `false` when another worker
    /// already owns it or it has already been answered.
    pub async fn begin_processing(&self, update_id: i64, chat_id: &str, message_id: i64) -> Result<bool> {
        let now = now_ms();
        let Some(existing) = self.find(update_id).await? else {
            sqlx::query(
                "insert into processed_updates (update_id, chat_id, message_id, processed_at, reply_text, conversation_state, suggested_replies, sent_at)
                 values (?, ?, ?, ?, null, null, null, null)",
            )
            .bind(update_id)
            .bind(chat_id)
            .bind(message_id)
            .bind(now)
            .execute(&self.database)
            .await?;
            return Ok(true);
        };
        if existing.sent_at.is_some() || (existing.reply_text.is_some() && existing.conversation_state.is_some()) {
            return Ok(false);
        }
        if now - existing.processed_at < INFLIGHT_TIMEOUT_MS {
            return Ok(false);
        }
        sqlx::query(
            "update processed_updates set
               chat_id = ?, message_id = ?, processed_at = ?,
               reply_text = null, conversation_state = null, suggested_replies = null, sent_at = null
             where update_id = ?",
        )
        .bind(chat_id)
        .bind(message_id)
        .bind(now)
        .bind(update_id)
        .execute(&self.database)
        .await?;
        Ok(true)
    }

    pub async fn clear_processing(&self, update_id: i64) -> Result<()> {
        sqlx::query(
            "delete from processed_updates
             where update_id = ? and sent_at is null and reply_text is null and conversation_state is null",
        )
        .bind(update_id)
        .execute(&self.database)
        .await?;
        Ok(())
    }

    /// Upserts only the bookkeeping columns so an already stored pending reply
    /// survives, matching TypeORM's partial `save` semantics.
    pub async fn mark_processed(&self, update_id: i64, chat_id: &str, message_id: i64) -> Result<()> {
        let now = now_ms();
        sqlx::query(
            "insert into processed_updates (update_id, chat_id, message_id, processed_at, reply_text, conversation_state, suggested_replies, sent_at)
             values (?, ?, ?, ?, null, null, null, ?)
             on conflict(update_id) do update set
               chat_id = excluded.chat_id,
               message_id = excluded.message_id,
               processed_at = excluded.processed_at,
               sent_at = excluded.sent_at",
        )
        .bind(update_id)
        .bind(chat_id)
        .bind(message_id)
        .bind(now)
        .bind(now)
        .execute(&self.database)
        .await?;
        Ok(())
    }

    pub async fn save_pending_reply(&self, update_id: i64, chat_id: &str, message_id: i64, result: &ReplyResult) -> Result<()> {
        sqlx::query(
            "insert into processed_updates (update_id, chat_id, message_id, processed_at, reply_text, conversation_state, suggested_replies, sent_at)
             values (?, ?, ?, ?, ?, ?, ?, null)
             on conflict(update_id) do update set
               chat_id = excluded.chat_id,
               message_id = excluded.message_id,
               processed_at = excluded.processed_at,
               reply_text = excluded.reply_text,
               conversation_state = excluded.conversation_state,
               suggested_replies = excluded.suggested_replies,
               sent_at = excluded.sent_at",
        )
        .bind(update_id)
        .bind(chat_id)
        .bind(message_id)
        .bind(now_ms())
        .bind(&result.text)
        .bind(result.conversation_state.as_deref())
        .bind(serde_json::to_string(&result.suggested_replies)?)
        .execute(&self.database)
        .await?;
        Ok(())
    }

    pub async fn prune_sent_before(&self, cutoff: i64) -> Result<u64> {
        let result = sqlx::query("delete from processed_updates where sent_at is not null and processed_at < ?")
            .bind(cutoff)
            .execute(&self.database)
            .await?;
        Ok(result.rows_affected())
    }
}
