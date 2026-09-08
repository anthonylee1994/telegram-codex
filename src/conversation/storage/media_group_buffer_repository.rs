use std::sync::Arc;

use anyhow::Result;
use sqlx::Row;

use crate::clock::now_ms;
use crate::conversation::storage::media_group_merger::MediaGroupMerger;
use crate::database::Database;
use crate::telegram::shared::inbound_message::InboundMessage;

#[derive(Debug)]
pub enum FlushResult {
    Missing,
    Stale,
    Pending { wait_duration_seconds: f64 },
    Ready { message: InboundMessage },
}

#[derive(Clone, Copy, Debug)]
pub struct EnqueueResult {
    pub deadline_at: i64,
}

pub struct MediaGroupBufferRepository {
    database: Database,
    media_group_merger: Arc<MediaGroupMerger>,
}

impl MediaGroupBufferRepository {
    pub fn new(database: Database, media_group_merger: Arc<MediaGroupMerger>) -> Self {
        Self { database, media_group_merger }
    }

    /// Buffers one album photo and (re)extends the flush deadline.
    pub async fn enqueue(&self, message: &InboundMessage, wait_duration_seconds: f64) -> Result<(EnqueueResult, String)> {
        let deadline_at = now_ms() + (wait_duration_seconds * 1000.0).round() as i64;
        let key = build_key(message);
        sqlx::query(
            "insert into media_group_buffers (key, deadline_at) values (?, ?)
             on conflict(key) do update set deadline_at = excluded.deadline_at",
        )
        .bind(&key)
        .bind(deadline_at)
        .execute(&self.database)
        .await?;
        sqlx::query(
            "insert into media_group_messages (update_id, media_group_key, message_id, payload) values (?, ?, ?, ?)
             on conflict(update_id) do update set
               media_group_key = excluded.media_group_key,
               message_id = excluded.message_id,
               payload = excluded.payload",
        )
        .bind(message.update_id)
        .bind(&key)
        .bind(message.message_id)
        .bind(serde_json::to_string(message)?)
        .execute(&self.database)
        .await?;
        Ok((EnqueueResult { deadline_at }, key))
    }

    pub async fn flush(&self, key: &str, expected_deadline_at: i64) -> Result<FlushResult> {
        let row = sqlx::query("select deadline_at from media_group_buffers where key = ?")
            .bind(key)
            .fetch_optional(&self.database)
            .await?;
        let Some(row) = row else {
            return Ok(FlushResult::Missing);
        };
        let deadline_at: i64 = row.try_get("deadline_at")?;
        if deadline_at != expected_deadline_at {
            return Ok(FlushResult::Stale);
        }
        let wait_duration_ms = deadline_at - now_ms();
        if wait_duration_ms > 0 {
            return Ok(FlushResult::Pending {
                wait_duration_seconds: wait_duration_ms as f64 / 1000.0,
            });
        }

        let rows = sqlx::query("select payload from media_group_messages where media_group_key = ? order by message_id asc, update_id asc")
            .bind(key)
            .fetch_all(&self.database)
            .await?;
        sqlx::query("delete from media_group_messages where media_group_key = ?").bind(key).execute(&self.database).await?;
        sqlx::query("delete from media_group_buffers where key = ?").bind(key).execute(&self.database).await?;
        if rows.is_empty() {
            return Ok(FlushResult::Missing);
        }

        let mut messages: Vec<InboundMessage> = Vec::with_capacity(rows.len());
        for row in &rows {
            let payload: String = row.try_get("payload")?;
            messages.push(InboundMessage::from_json(&serde_json::from_str(&payload)?));
        }
        Ok(FlushResult::Ready {
            message: self.media_group_merger.merge(messages)?,
        })
    }
}

fn build_key(message: &InboundMessage) -> String {
    format!("{}:{}", message.chat_id, message.media_group_id.as_deref().unwrap_or("null"))
}
