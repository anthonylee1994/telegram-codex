pub mod migrations;

use std::path::Path;

use anyhow::{Context, Result};
use sqlx::SqlitePool;
use sqlx::sqlite::{SqliteConnectOptions, SqlitePoolOptions};

pub type Database = SqlitePool;

pub async fn connect(sqlite_db_path: &Path) -> Result<Database> {
    if let Some(parent) = sqlite_db_path.parent().filter(|parent| !parent.as_os_str().is_empty()) {
        std::fs::create_dir_all(parent).with_context(|| format!("Failed to create database directory {}", parent.display()))?;
    }
    let options = SqliteConnectOptions::new().filename(sqlite_db_path).create_if_missing(true).foreign_keys(true);
    let pool = SqlitePoolOptions::new()
        .max_connections(5)
        .connect_with(options)
        .await
        .with_context(|| format!("Failed to open sqlite database {}", sqlite_db_path.display()))?;
    migrations::run(&pool).await?;
    Ok(pool)
}
