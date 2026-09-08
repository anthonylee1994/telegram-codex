use std::sync::Arc;

use anyhow::Result;
use clap::{Parser, Subcommand};
use telegram_codex::config::AppConfig;
use telegram_codex::telegram::commands::telegram_task::BOT_COMMANDS;
use telegram_codex::{app, bootstrap_environment};

/// Replaces the `nest-commander` task runner.
#[derive(Parser)]
#[command(name = "task", about = "telegram-codex maintenance tasks")]
struct Cli {
    #[command(subcommand)]
    command: Task,
}

#[derive(Subcommand)]
enum Task {
    /// Configure Telegram webhook
    #[command(name = "telegram:set-webhook")]
    TelegramSetWebhook,
    /// Update Telegram bot commands
    #[command(name = "telegram:update-commands")]
    TelegramUpdateCommands,
}

#[tokio::main]
async fn main() -> Result<()> {
    bootstrap_environment()?;

    let cli = Cli::parse();
    let config = Arc::new(AppConfig::from_env()?);
    let telegram_client = app::build_telegram_gateway(config.clone());

    match cli.command {
        Task::TelegramSetWebhook => telegram_client.set_webhook(&config.webhook_url(), &config.telegram_webhook_secret).await,
        Task::TelegramUpdateCommands => telegram_client.set_my_commands(&BOT_COMMANDS).await,
    }
}
