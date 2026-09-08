use std::path::PathBuf;

use anyhow::Result;
use async_trait::async_trait;
use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct TelegramBotCommand {
    pub command: String,
    pub description: String,
}

#[derive(Clone, Debug, Default, Deserialize)]
pub struct TelegramUpdate {
    pub update_id: Option<i64>,
    pub message: Option<TelegramMessage>,
}

#[derive(Clone, Debug, Default, Deserialize)]
pub struct TelegramMessage {
    pub message_id: Option<i64>,
    pub from: Option<TelegramUser>,
    pub chat: Option<TelegramChat>,
    pub text: Option<String>,
    pub caption: Option<String>,
    pub photo: Option<Vec<TelegramPhotoSize>>,
    pub document: Option<TelegramDocument>,
    pub media_group_id: Option<String>,
    pub reply_to_message: Option<Box<TelegramMessage>>,
}

#[derive(Clone, Debug, Default, Deserialize)]
pub struct TelegramUser {
    pub id: Option<IdValue>,
}

#[derive(Clone, Debug, Default, Deserialize)]
pub struct TelegramChat {
    pub id: Option<IdValue>,
}

#[derive(Clone, Debug, Default, Deserialize)]
pub struct TelegramPhotoSize {
    pub file_id: Option<String>,
    pub file_size: Option<i64>,
}

#[derive(Clone, Debug, Default, Deserialize)]
pub struct TelegramDocument {
    pub file_id: Option<String>,
    pub mime_type: Option<String>,
    pub file_name: Option<String>,
}

/// Telegram ids arrive as numbers but the TypeScript version also tolerated
/// strings, so both shapes are accepted and stringified the same way.
#[derive(Clone, Debug, Deserialize)]
#[serde(untagged)]
pub enum IdValue {
    Number(i64),
    Text(String),
}

impl IdValue {
    pub fn as_string(&self) -> String {
        match self {
            IdValue::Number(value) => value.to_string(),
            IdValue::Text(value) => value.clone(),
        }
    }
}

/// Outbound boundary towards the Telegram API.
///
/// `withTypingStatus` is not part of the trait because a generic closure would
/// make it non dyn-compatible; `TypingStatusService` composes it from
/// `send_chat_action` instead.
#[async_trait]
pub trait TelegramGateway: Send + Sync {
    async fn download_file_to_temp(&self, file_id: &str) -> Result<PathBuf>;

    async fn send_message(&self, chat_id: &str, text: Option<&str>, suggested_replies: &[String], remove_keyboard: bool) -> Result<()>;

    async fn send_chat_action(&self, chat_id: &str, action: &str) -> Result<()>;

    async fn set_webhook(&self, url: &str, secret_token: &str) -> Result<()>;

    async fn set_my_commands(&self, commands: &[TelegramBotCommand]) -> Result<()>;
}

pub const MAX_SUGGESTED_REPLIES: usize = 3;
pub const MAX_SUGGESTED_REPLY_LENGTH: usize = 40;
pub const API_BASE: &str = "https://api.telegram.org/bot";
pub const FILE_API_BASE: &str = "https://api.telegram.org/file/bot";

pub const IMAGE_MIME_TYPE_PREFIXES: &[&str] = &["image/"];
pub const IMAGE_EXTENSIONS: &[&str] = &[".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".tiff", ".heic", ".heif"];
