pub mod migrations;

use std::path::Path;
use std::time::Duration;

use anyhow::{Context, Result};
use sqlx::SqlitePool;
use sqlx::sqlite::{SqliteConnectOptions, SqlitePoolOptions};

pub type Database = SqlitePool;

/// Every pooled connection carries its own page cache and sqlite serialises
/// writes regardless, so a wider pool buys memory rather than throughput. No
/// query holds a connection while awaiting another one, so two cannot deadlock.
const MAX_CONNECTIONS: u32 = 2;
/// Negative values are the sqlite convention for KiB instead of pages. The
/// default is `-2000`, which is generous for tables this small.
const PAGE_CACHE_KIB: &str = "-512";
/// Releases the second connection soon after a burst instead of sqlx's 10
/// minute default.
const IDLE_TIMEOUT: Duration = Duration::from_secs(60);

pub async fn connect(sqlite_db_path: &Path) -> Result<Database> {
    if let Some(parent) = sqlite_db_path.parent().filter(|parent| !parent.as_os_str().is_empty()) {
        std::fs::create_dir_all(parent).with_context(|| format!("Failed to create database directory {}", parent.display()))?;
    }
    let options = SqliteConnectOptions::new()
        .filename(sqlite_db_path)
        .create_if_missing(true)
        .foreign_keys(true)
        .pragma("cache_size", PAGE_CACHE_KIB);
    let pool = SqlitePoolOptions::new()
        .max_connections(MAX_CONNECTIONS)
        .idle_timeout(IDLE_TIMEOUT)
        .connect_with(options)
        .await
        .with_context(|| format!("Failed to open sqlite database {}", sqlite_db_path.display()))?;
    migrations::run(&pool).await?;
    Ok(pool)
}

#[cfg(test)]
mod tests {
    use super::*;

    /// `cache_size` is per connection and never stored in the file, so this is
    /// the only place the setting is observable at all.
    #[tokio::test]
    async fn applies_the_page_cache_pragma_to_pooled_connections() {
        let temp_dir = tempfile::tempdir().unwrap();
        let pool = connect(&temp_dir.path().join("app.db")).await.unwrap();

        let cache_size: i64 = sqlx::query_scalar("pragma cache_size").fetch_one(&pool).await.unwrap();

        assert_eq!(cache_size.to_string(), PAGE_CACHE_KIB);
        pool.close().await;
    }
}
