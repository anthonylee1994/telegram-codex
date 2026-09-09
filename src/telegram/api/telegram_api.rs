use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use anyhow::{Context, Result, bail};
use async_trait::async_trait;
use futures_util::StreamExt;
use serde::Deserialize;
use tokio::io::{AsyncWriteExt, BufWriter};
use tracing::info;

use crate::config::AppConfig;
use crate::telegram::shared::telegram_message_formatter::TelegramMessageFormatter;
use crate::telegram::shared::telegram_types::{API_BASE, FILE_API_BASE, TelegramBotCommand, TelegramGateway};

#[derive(Deserialize)]
struct TelegramApiResponse<T> {
    #[serde(default)]
    ok: bool,
    result: Option<T>,
}

#[derive(Deserialize)]
struct TelegramFileResult {
    file_path: Option<String>,
}

pub struct TelegramApi {
    config: Arc<AppConfig>,
    formatter: Arc<TelegramMessageFormatter>,
    client: reqwest::Client,
}

impl TelegramApi {
    pub fn new(config: Arc<AppConfig>, formatter: Arc<TelegramMessageFormatter>) -> Self {
        Self {
            config,
            formatter,
            client: reqwest::Client::new(),
        }
    }

    async fn get_file(&self, file_id: &str) -> Result<TelegramFileResult> {
        let url = format!("{API_BASE}{}/getFile", self.config.telegram_bot_token);
        let response = self.client.get(&url).query(&[("file_id", file_id)]).send().await.context("Failed to call Telegram getFile")?;
        if !response.status().is_success() {
            bail!("Failed to call Telegram getFile: HTTP {}", response.status().as_u16());
        }
        let payload: TelegramApiResponse<TelegramFileResult> = response.json().await.context("Failed to call Telegram getFile: invalid response")?;
        match (payload.ok, payload.result) {
            (true, Some(result)) => Ok(result),
            _ => bail!("Failed to call Telegram getFile: invalid response"),
        }
    }

    /// Streams the body straight to disk. Telegram allows bot downloads up to
    /// 20MB, which must never sit on the heap in one piece.
    async fn stream_to_file(&self, url: &str, output_path: &Path) -> Result<()> {
        let response = self.client.get(url).send().await.context("Failed to download Telegram file")?;
        if !response.status().is_success() {
            bail!("Failed to download Telegram file: HTTP {}", response.status().as_u16());
        }
        let mut body = response.bytes_stream();
        let mut writer = BufWriter::new(tokio::fs::File::create(output_path).await?);
        while let Some(chunk) = body.next().await {
            writer.write_all(&chunk.context("Failed to download Telegram file")?).await?;
        }
        writer.flush().await?;
        Ok(())
    }

    async fn post_form(&self, method_name: &str, params: &HashMap<&str, String>) -> Result<()> {
        let url = format!("{API_BASE}{}/{method_name}", self.config.telegram_bot_token);
        let response = self.client.post(&url).form(params).send().await.with_context(|| format!("Failed to call Telegram {method_name}"))?;
        if !response.status().is_success() {
            bail!("Failed to call Telegram {method_name}: HTTP {}", response.status().as_u16());
        }
        let payload: TelegramApiResponse<serde_json::Value> = response.json().await.with_context(|| format!("Failed to call Telegram {method_name}: invalid response"))?;
        if !payload.ok {
            bail!("Failed to call Telegram {method_name}: invalid response");
        }
        Ok(())
    }
}

#[async_trait]
impl TelegramGateway for TelegramApi {
    async fn download_file_to_temp(&self, file_id: &str) -> Result<PathBuf> {
        let file = self.get_file(file_id).await?;
        let Some(file_path) = file.file_path.filter(|file_path| !file_path.is_empty()) else {
            bail!("Telegram getFile did not include a file path");
        };
        let temp_dir = tempfile::Builder::new().prefix("telegram-codex-file-").tempdir()?;
        let file_name = PathBuf::from(&file_path).file_name().map(PathBuf::from).unwrap_or_else(|| PathBuf::from("download"));
        let output_path = temp_dir.path().join(file_name);

        let url = format!("{FILE_API_BASE}{}/{file_path}", self.config.telegram_bot_token);
        self.stream_to_file(&url, &output_path).await?;
        // The caller removes the whole directory afterwards, so the guard is
        // only leaked once the download succeeded. A failure drops the
        // directory and takes any partially written file with it.
        let _ = temp_dir.keep();
        Ok(output_path)
    }

    async fn send_message(&self, chat_id: &str, text: Option<&str>, suggested_replies: &[String], remove_keyboard: bool) -> Result<()> {
        let normalized = self.formatter.normalize_reply(text, suggested_replies)?;
        let reply_markup = self.formatter.build_reply_markup(&normalized.suggested_replies, remove_keyboard);
        let mut params = HashMap::from([
            ("chat_id", chat_id.to_owned()),
            ("text", self.formatter.format_for_telegram(Some(&normalized.text))),
            ("parse_mode", "HTML".to_owned()),
        ]);
        if let Some(reply_markup) = reply_markup {
            params.insert("reply_markup", serde_json::to_string(&reply_markup)?);
        }
        self.post_form("sendMessage", &params).await
    }

    async fn send_chat_action(&self, chat_id: &str, action: &str) -> Result<()> {
        self.post_form("sendChatAction", &HashMap::from([("chat_id", chat_id.to_owned()), ("action", action.to_owned())])).await
    }

    async fn set_webhook(&self, url: &str, secret_token: &str) -> Result<()> {
        self.post_form("setWebhook", &HashMap::from([("url", url.to_owned()), ("secret_token", secret_token.to_owned())]))
            .await?;
        info!("Telegram webhook configured url={url}");
        Ok(())
    }

    async fn set_my_commands(&self, commands: &[TelegramBotCommand]) -> Result<()> {
        self.post_form("setMyCommands", &HashMap::from([("commands", serde_json::to_string(commands)?)])).await?;
        info!("Telegram commands updated count={}", commands.len());
        Ok(())
    }
}
