use std::sync::Arc;

use anyhow::{Context, Result};
use telegram_codex::config::AppConfig;
use telegram_codex::{app, bootstrap_environment};
use tokio::signal;
use tracing::info;

/// A webhook bot is almost entirely waiting on `codex exec`, Telegram, and
/// sqlite, so the default runtime (one worker per core, 512 blocking threads)
/// reserves far more thread stack than the workload ever uses.
const WORKER_THREADS: usize = 2;
const MAX_BLOCKING_THREADS: usize = 8;

fn main() -> Result<()> {
    tokio::runtime::Builder::new_multi_thread()
        .worker_threads(WORKER_THREADS)
        .max_blocking_threads(MAX_BLOCKING_THREADS)
        .enable_all()
        .build()
        .context("Failed to build the tokio runtime")?
        .block_on(run())
}

async fn run() -> Result<()> {
    bootstrap_environment()?;

    let config = Arc::new(AppConfig::from_env()?);
    if let Some(parent) = config.sqlite_db_path.parent().filter(|parent| !parent.as_os_str().is_empty()) {
        std::fs::create_dir_all(parent).with_context(|| format!("Failed to create directory {}", parent.display()))?;
    }

    let app = app::build(config.clone()).await?;
    let listener = tokio::net::TcpListener::bind(("0.0.0.0", config.port))
        .await
        .with_context(|| format!("Failed to bind port {}", config.port))?;
    info!("telegram-codex listening on port {}", config.port);

    axum::serve(listener, app::router(app.state.clone())).with_graceful_shutdown(shutdown_signal()).await?;

    app.job_scheduler.shutdown();
    app.database.close().await;
    Ok(())
}

async fn shutdown_signal() {
    let interrupt = async {
        signal::ctrl_c().await.expect("failed to listen for ctrl-c");
    };

    #[cfg(unix)]
    let terminate = async {
        signal::unix::signal(signal::unix::SignalKind::terminate()).expect("failed to listen for SIGTERM").recv().await;
    };

    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        () = interrupt => {},
        () = terminate => {},
    }
    info!("Shutting down");
}
