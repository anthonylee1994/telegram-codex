use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use crate::clock::now_ms;
use crate::config::AppConfig;

/// In-memory sliding window limiter, one window per chat.
pub struct ChatRateLimiter {
    config: Arc<AppConfig>,
    hits: Mutex<HashMap<String, Vec<i64>>>,
}

impl ChatRateLimiter {
    pub fn new(config: Arc<AppConfig>) -> Self {
        Self {
            config,
            hits: Mutex::new(HashMap::new()),
        }
    }

    pub fn allow(&self, chat_id: &str) -> bool {
        let now = now_ms();
        let mut hits = self.hits.lock().expect("rate limiter mutex poisoned");
        let chat_hits = hits.entry(chat_id.to_owned()).or_default();
        chat_hits.retain(|timestamp| now - timestamp < self.config.rate_limit_window_ms);
        if chat_hits.len() >= self.config.rate_limit_max_messages {
            return false;
        }
        chat_hits.push(now);
        true
    }
}

#[cfg(test)]
mod tests {
    use std::path::PathBuf;

    use super::*;

    fn limiter(max_messages: usize) -> ChatRateLimiter {
        ChatRateLimiter::new(Arc::new(AppConfig {
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
            rate_limit_max_messages: max_messages,
            codex_sandbox_mode: "danger-full-access".to_owned(),
        }))
    }

    #[test]
    fn blocks_after_reaching_the_limit() {
        let limiter = limiter(2);
        assert!(limiter.allow("3"));
        assert!(limiter.allow("3"));
        assert!(!limiter.allow("3"));
    }

    #[test]
    fn tracks_windows_per_chat() {
        let limiter = limiter(1);
        assert!(limiter.allow("3"));
        assert!(limiter.allow("4"));
        assert!(!limiter.allow("3"));
    }
}
