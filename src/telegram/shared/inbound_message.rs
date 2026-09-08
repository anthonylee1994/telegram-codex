use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct ProcessingUpdate {
    pub update_id: i64,
    pub message_id: i64,
}

/// Normalised inbound Telegram message.
///
/// Serialisation stays wire compatible with the TypeScript `toJSON` output so
/// media group payloads already stored in sqlite keep loading.
#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InboundMessage {
    pub chat_id: String,
    pub image_file_ids: Vec<String>,
    pub media_group_id: Option<String>,
    pub message_id: i64,
    pub processing_updates: Vec<ProcessingUpdate>,
    pub reply_to_image_file_ids: Vec<String>,
    pub reply_to_text: Option<String>,
    pub text: Option<String>,
    pub user_id: String,
    pub update_id: i64,
}

#[derive(Default)]
pub struct InboundMessageParams {
    pub chat_id: String,
    pub image_file_ids: Vec<String>,
    pub media_group_id: Option<String>,
    pub message_id: i64,
    pub processing_updates: Vec<ProcessingUpdate>,
    pub reply_to_image_file_ids: Vec<String>,
    pub reply_to_text: Option<String>,
    pub text: Option<String>,
    pub user_id: String,
    pub update_id: i64,
}

impl InboundMessage {
    pub fn new(params: InboundMessageParams) -> Self {
        let mut processing_updates = if params.processing_updates.is_empty() {
            vec![ProcessingUpdate {
                update_id: params.update_id,
                message_id: params.message_id,
            }]
        } else {
            params.processing_updates
        };
        processing_updates.sort_by(|left, right| left.message_id.cmp(&right.message_id).then(left.update_id.cmp(&right.update_id)));

        Self {
            chat_id: params.chat_id,
            image_file_ids: normalize_strings(params.image_file_ids),
            media_group_id: blank_to_none(params.media_group_id),
            message_id: params.message_id,
            processing_updates,
            reply_to_image_file_ids: normalize_strings(params.reply_to_image_file_ids),
            reply_to_text: blank_to_none(params.reply_to_text),
            text: blank_to_none(params.text),
            user_id: params.user_id,
            update_id: params.update_id,
        }
    }

    pub fn for_merged_media_group(primary: &InboundMessage, image_file_ids: Vec<String>, processing_updates: Vec<ProcessingUpdate>, text: Option<String>) -> Self {
        Self::new(InboundMessageParams {
            chat_id: primary.chat_id.clone(),
            image_file_ids,
            media_group_id: primary.media_group_id.clone(),
            message_id: primary.message_id,
            processing_updates,
            reply_to_image_file_ids: Vec::new(),
            reply_to_text: None,
            text,
            user_id: primary.user_id.clone(),
            update_id: primary.update_id,
        })
    }

    pub fn media_group(&self) -> bool {
        self.media_group_id.is_some()
    }

    pub fn unsupported(&self) -> bool {
        self.text.is_none() && self.image_file_ids.is_empty()
    }

    pub fn image_count(&self) -> usize {
        self.image_file_ids.len()
    }

    pub fn text_or_empty(&self) -> &str {
        self.text.as_deref().unwrap_or("")
    }

    pub fn effective_image_file_ids(&self) -> &[String] {
        if self.image_file_ids.is_empty() { &self.reply_to_image_file_ids } else { &self.image_file_ids }
    }

    pub fn from_json(payload: &Value) -> Self {
        Self::new(InboundMessageParams {
            chat_id: string_field(payload, "chatId"),
            image_file_ids: string_array_field(payload, "imageFileIds"),
            media_group_id: optional_string_field(payload, "mediaGroupId"),
            message_id: number_field(payload, "messageId"),
            processing_updates: processing_updates_field(payload),
            reply_to_image_file_ids: string_array_field(payload, "replyToImageFileIds"),
            reply_to_text: optional_string_field(payload, "replyToText"),
            text: optional_string_field(payload, "text"),
            user_id: string_field(payload, "userId"),
            update_id: number_field(payload, "updateId"),
        })
    }
}

fn blank_to_none(value: Option<String>) -> Option<String> {
    value.map(|value| value.trim().to_owned()).filter(|value| !value.is_empty())
}

fn normalize_strings(values: Vec<String>) -> Vec<String> {
    let mut normalized: Vec<String> = Vec::new();
    for value in values {
        if let Some(value) = blank_to_none(Some(value))
            && !normalized.contains(&value)
        {
            normalized.push(value);
        }
    }
    normalized
}

fn string_field(payload: &Value, key: &str) -> String {
    match payload.get(key) {
        Some(Value::String(value)) => value.clone(),
        Some(Value::Number(value)) => value.to_string(),
        _ => String::new(),
    }
}

fn optional_string_field(payload: &Value, key: &str) -> Option<String> {
    payload.get(key).and_then(Value::as_str).map(str::to_owned)
}

fn number_field(payload: &Value, key: &str) -> i64 {
    match payload.get(key) {
        Some(Value::Number(value)) => value.as_i64().unwrap_or(0),
        Some(Value::String(value)) => value.parse().unwrap_or(0),
        _ => 0,
    }
}

fn string_array_field(payload: &Value, key: &str) -> Vec<String> {
    payload
        .get(key)
        .and_then(Value::as_array)
        .map(|values| values.iter().filter_map(Value::as_str).map(str::to_owned).collect())
        .unwrap_or_default()
}

fn processing_updates_field(payload: &Value) -> Vec<ProcessingUpdate> {
    payload
        .get("processingUpdates")
        .and_then(Value::as_array)
        .map(|values| {
            values
                .iter()
                .map(|value| ProcessingUpdate {
                    update_id: number_field(value, "update_id"),
                    message_id: number_field(value, "message_id"),
                })
                .collect()
        })
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn message() -> InboundMessage {
        InboundMessage::new(InboundMessageParams {
            chat_id: "3".to_owned(),
            image_file_ids: vec![" a ".to_owned(), "a".to_owned(), String::new(), "b".to_owned()],
            message_id: 10,
            text: Some("  hi  ".to_owned()),
            user_id: "5".to_owned(),
            update_id: 99,
            ..Default::default()
        })
    }

    #[test]
    fn trims_and_deduplicates_file_ids() {
        assert_eq!(message().image_file_ids, vec!["a".to_owned(), "b".to_owned()]);
    }

    #[test]
    fn defaults_processing_updates_to_own_ids() {
        assert_eq!(message().processing_updates, vec![ProcessingUpdate { update_id: 99, message_id: 10 }]);
    }

    #[test]
    fn round_trips_through_json() {
        let original = message();
        let restored = InboundMessage::from_json(&serde_json::to_value(&original).unwrap());
        assert_eq!(restored.chat_id, original.chat_id);
        assert_eq!(restored.text, original.text);
        assert_eq!(restored.image_file_ids, original.image_file_ids);
        assert_eq!(restored.processing_updates, original.processing_updates);
    }
}
