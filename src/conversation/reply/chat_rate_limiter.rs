use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use crate::clock::now_ms;
use crate::config::AppConfig;

#[derive(Default)]
struct Windows {
    hits: HashMap<String, Vec<i64>>,
    swept_at: i64,
}

/// In-memory sliding window limiter, one window per chat.
pub struct ChatRateLimiter {
    config: Arc<AppConfig>,
    windows: Mutex<Windows>,
}

impl ChatRateLimiter {
    pub fn new(config: Arc<AppConfig>) -> Self {
        Self {
            config,
            windows: Mutex::new(Windows {
                hits: HashMap::new(),
                swept_at: now_ms(),
            }),
        }
    }

    pub fn allow(&self, chat_id: &str) -> bool {
        let now = now_ms();
        let window_ms = self.config.rate_limit_window_ms;
        let max_messages = self.config.rate_limit_max_messages;
        let mut windows = self.windows.lock().expect("rate limiter mutex poisoned");

        // Only the chats calling `allow` prune themselves below, so quiet chats
        // need this periodic pass or their entry lives for the whole process.
        if now - windows.swept_at >= window_ms {
            sweep(&mut windows, now, window_ms);
        }

        match windows.hits.get_mut(chat_id) {
            Some(chat_hits) => {
                chat_hits.retain(|timestamp| now - timestamp < window_ms);
                if chat_hits.len() >= max_messages {
                    return false;
                }
                chat_hits.push(now);
                true
            }
            None => {
                if max_messages == 0 {
                    return false;
                }
                // Looking up before inserting keeps the repeat path free of the
                // unconditional key allocation `entry` would require, and the
                // window can never outgrow `max_messages`.
                let mut chat_hits = Vec::with_capacity(max_messages);
                chat_hits.push(now);
                windows.hits.insert(chat_id.to_owned(), chat_hits);
                true
            }
        }
    }
}

/// Drops expired timestamps, then every chat left with an empty window, and
/// finally releases the table capacity those chats were holding.
fn sweep(windows: &mut Windows, now: i64, window_ms: i64) {
    let before = windows.hits.len();
    windows.hits.retain(|_, chat_hits| {
        chat_hits.retain(|timestamp| now - timestamp < window_ms);
        !chat_hits.is_empty()
    });
    if windows.hits.len() < before {
        windows.hits.shrink_to_fit();
    }
    windows.swept_at = now;
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

    #[test]
    fn keeps_one_entry_per_chat() {
        let limiter = limiter(5);
        for _ in 0..10 {
            let _ = limiter.allow("3");
        }
        assert_eq!(limiter.windows.lock().unwrap().hits.len(), 1);
    }

    #[test]
    fn sweep_evicts_chats_that_went_quiet() {
        let mut windows = Windows {
            hits: HashMap::from([("quiet".to_owned(), vec![1_000]), ("busy".to_owned(), vec![1_000, 9_500])]),
            swept_at: 0,
        };

        sweep(&mut windows, 10_000, 5_000);

        assert_eq!(windows.hits.keys().collect::<Vec<_>>(), vec!["busy"]);
        assert_eq!(windows.hits["busy"], vec![9_500]);
        assert_eq!(windows.swept_at, 10_000);
    }
}
