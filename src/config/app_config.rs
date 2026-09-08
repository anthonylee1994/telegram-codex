use std::path::{Component, Path, PathBuf};

use anyhow::{Context, Result, bail};

/// Environment backed configuration. Replaces the NestJS `AppConfigService`
/// wrapper: in Rust the struct itself is the read only accessor.
#[derive(Clone, Debug)]
pub struct AppConfig {
    pub port: u16,
    pub base_url: String,
    pub telegram_bot_token: String,
    pub telegram_webhook_secret: String,
    pub allowed_telegram_user_ids: Vec<String>,
    pub sqlite_db_path: PathBuf,
    pub codex_exec_timeout_seconds: u64,
    pub max_media_group_images: usize,
    pub session_ttl_days: i64,
    pub media_group_wait_ms: i64,
    pub rate_limit_window_ms: i64,
    pub rate_limit_max_messages: usize,
    pub codex_sandbox_mode: String,
}

impl AppConfig {
    pub fn from_env() -> Result<Self> {
        Ok(Self {
            port: number_value("PORT", 3000.0)? as u16,
            base_url: trim_trailing_slashes(&required_string("BASE_URL")?),
            telegram_bot_token: required_string("TELEGRAM_BOT_TOKEN")?,
            telegram_webhook_secret: required_string("TELEGRAM_WEBHOOK_SECRET")?,
            allowed_telegram_user_ids: comma_separated("ALLOWED_TELEGRAM_USER_IDS"),
            sqlite_db_path: normalize_path(&optional_string("SQLITE_DB_PATH").unwrap_or_else(|| "./data/app.db".to_owned())),
            codex_exec_timeout_seconds: number_value("CODEX_EXEC_TIMEOUT_SECONDS", 300.0)? as u64,
            max_media_group_images: number_value("MAX_MEDIA_GROUP_IMAGES", 10.0)? as usize,
            session_ttl_days: number_value("SESSION_TTL_DAYS", 7.0)? as i64,
            media_group_wait_ms: number_value("MEDIA_GROUP_WAIT_MS", 1200.0)? as i64,
            rate_limit_window_ms: number_value("RATE_LIMIT_WINDOW_MS", 10000.0)? as i64,
            rate_limit_max_messages: number_value("RATE_LIMIT_MAX_MESSAGES", 5.0)? as usize,
            codex_sandbox_mode: optional_string("CODEX_SANDBOX_MODE").unwrap_or_else(|| "danger-full-access".to_owned()),
        })
    }

    pub fn webhook_url(&self) -> String {
        format!("{}/telegram/webhook", self.base_url)
    }

    pub fn session_ttl_ms(&self) -> i64 {
        self.session_ttl_days * 24 * 60 * 60 * 1000
    }
}

fn number_value(name: &str, fallback: f64) -> Result<f64> {
    let raw = match std::env::var(name) {
        Ok(value) => value,
        Err(_) => return Ok(fallback),
    };
    if raw.trim().is_empty() {
        return Ok(fallback);
    }
    let value: f64 = raw.trim().parse().with_context(|| format!("{name} must be a number"))?;
    if !value.is_finite() {
        bail!("{name} must be a number");
    }
    Ok(value)
}

fn required_string(name: &str) -> Result<String> {
    match optional_string(name) {
        Some(value) => Ok(value),
        None => bail!("{name} is required"),
    }
}

fn optional_string(name: &str) -> Option<String> {
    std::env::var(name).ok().map(|value| value.trim().to_owned()).filter(|value| !value.is_empty())
}

fn comma_separated(name: &str) -> Vec<String> {
    std::env::var(name)
        .unwrap_or_default()
        .split(',')
        .map(|value| value.trim().to_owned())
        .filter(|value| !value.is_empty())
        .collect()
}

fn trim_trailing_slashes(value: &str) -> String {
    value.trim_end_matches('/').to_owned()
}

/// Mirrors `path.normalize`: drops redundant `./` segments and resolves `..`.
fn normalize_path(value: &str) -> PathBuf {
    let mut normalized = PathBuf::new();
    for component in Path::new(value).components() {
        match component {
            Component::CurDir => {}
            Component::ParentDir => {
                if !normalized.pop() {
                    normalized.push("..");
                }
            }
            other => normalized.push(other.as_os_str()),
        }
    }
    if normalized.as_os_str().is_empty() {
        normalized.push(".");
    }
    normalized
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalizes_relative_paths() {
        assert_eq!(normalize_path("./data/app.db"), PathBuf::from("data/app.db"));
        assert_eq!(normalize_path("data/../data/app.db"), PathBuf::from("data/app.db"));
    }

    #[test]
    fn trims_trailing_slashes_from_base_url() {
        assert_eq!(trim_trailing_slashes("https://example.com///"), "https://example.com");
    }
}
