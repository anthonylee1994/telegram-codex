use std::sync::Arc;

use anyhow::Result;
use async_trait::async_trait;
use axum::Json;
use axum::extract::State;
use axum::http::{HeaderMap, StatusCode};
use tracing::error;

use crate::app::AppState;
use crate::config::AppConfig;
use crate::health::health_controller::ApiStatusResponse;
use crate::telegram::shared::telegram_types::TelegramUpdate;
use crate::telegram::webhook::telegram_webhook_service::TelegramWebhookService;

const SECRET_TOKEN_HEADER: &str = "x-telegram-bot-api-secret-token";

/// Seam so the controller can be unit tested without a full service graph.
#[async_trait]
pub trait WebhookHandler: Send + Sync {
    async fn handle(&self, update: Option<&TelegramUpdate>) -> Result<()>;
}

#[async_trait]
impl WebhookHandler for TelegramWebhookService {
    async fn handle(&self, update: Option<&TelegramUpdate>) -> Result<()> {
        TelegramWebhookService::handle(self, update).await
    }
}

/// `POST /telegram/webhook`
pub async fn create(State(state): State<AppState>, headers: HeaderMap, payload: Option<Json<TelegramUpdate>>) -> (StatusCode, Json<ApiStatusResponse>) {
    let secret_token = headers.get(SECRET_TOKEN_HEADER).and_then(|value| value.to_str().ok());
    handle_webhook(&state.config, state.webhook_handler.as_ref(), secret_token, payload.map(|Json(payload)| payload).as_ref()).await
}

pub async fn handle_webhook(config: &AppConfig, webhook_handler: &dyn WebhookHandler, secret_token: Option<&str>, payload: Option<&TelegramUpdate>) -> (StatusCode, Json<ApiStatusResponse>) {
    if Some(config.telegram_webhook_secret.as_str()) != secret_token {
        return (StatusCode::UNAUTHORIZED, Json(ApiStatusResponse { ok: false }));
    }
    match webhook_handler.handle(payload).await {
        Ok(()) => (StatusCode::OK, Json(ApiStatusResponse { ok: true })),
        Err(error) => {
            error!("Failed to handle Telegram webhook: {error:?}");
            (StatusCode::INTERNAL_SERVER_ERROR, Json(ApiStatusResponse { ok: false }))
        }
    }
}

pub type SharedWebhookHandler = Arc<dyn WebhookHandler>;

#[cfg(test)]
mod tests {
    use std::path::PathBuf;
    use std::sync::atomic::{AtomicUsize, Ordering};

    use super::*;

    #[derive(Default)]
    struct RecordingHandler {
        calls: AtomicUsize,
        fail: bool,
    }

    #[async_trait]
    impl WebhookHandler for RecordingHandler {
        async fn handle(&self, _update: Option<&TelegramUpdate>) -> Result<()> {
            self.calls.fetch_add(1, Ordering::Relaxed);
            if self.fail { Err(anyhow::anyhow!("boom")) } else { Ok(()) }
        }
    }

    fn config() -> AppConfig {
        AppConfig {
            port: 3000,
            base_url: "https://example.com".to_owned(),
            telegram_bot_token: "token".to_owned(),
            telegram_webhook_secret: "secret".to_owned(),
            allowed_telegram_user_ids: Vec::new(),
            sqlite_db_path: PathBuf::from("./data/app.db"),
            codex_exec_timeout_seconds: 300,
            max_media_group_images: 10,
            session_ttl_days: 7,
            media_group_wait_ms: 1200,
            rate_limit_window_ms: 10_000,
            rate_limit_max_messages: 5,
            codex_sandbox_mode: "danger-full-access".to_owned(),
        }
    }

    #[tokio::test]
    async fn rejects_invalid_secret() {
        let handler = RecordingHandler::default();
        let update = TelegramUpdate { update_id: Some(1), message: None };

        let (status, body) = handle_webhook(&config(), &handler, Some("bad"), Some(&update)).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
        assert!(!body.ok);
        assert_eq!(handler.calls.load(Ordering::Relaxed), 0);
    }

    #[tokio::test]
    async fn rejects_missing_secret() {
        let handler = RecordingHandler::default();

        let (status, _) = handle_webhook(&config(), &handler, None, None).await;

        assert_eq!(status, StatusCode::UNAUTHORIZED);
    }

    #[tokio::test]
    async fn accepts_valid_secret() {
        let handler = RecordingHandler::default();
        let update = TelegramUpdate { update_id: Some(1), message: None };

        let (status, body) = handle_webhook(&config(), &handler, Some("secret"), Some(&update)).await;

        assert_eq!(status, StatusCode::OK);
        assert!(body.ok);
        assert_eq!(handler.calls.load(Ordering::Relaxed), 1);
    }

    #[tokio::test]
    async fn reports_handler_failures() {
        let handler = RecordingHandler { fail: true, ..Default::default() };

        let (status, body) = handle_webhook(&config(), &handler, Some("secret"), None).await;

        assert_eq!(status, StatusCode::INTERNAL_SERVER_ERROR);
        assert!(!body.ok);
    }
}
