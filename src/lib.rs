pub mod app;
pub mod clock;
pub mod codex;
pub mod config;
pub mod conversation;
pub mod database;
pub mod health;
pub mod telegram;

use anyhow::Result;

/// Loads `.env` and installs the tracing subscriber. Shared by both binaries.
pub fn bootstrap_environment() -> Result<()> {
    dotenvy::dotenv().ok();
    tracing_subscriber::fmt()
        .with_env_filter(tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| tracing_subscriber::EnvFilter::new("info")))
        .init();
    Ok(())
}
