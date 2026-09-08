use crate::telegram::shared::inbound_message::{InboundMessage, InboundMessageParams};
use crate::telegram::shared::message_constants;
use crate::telegram::shared::telegram_types::{IMAGE_EXTENSIONS, IMAGE_MIME_TYPE_PREFIXES, TelegramDocument, TelegramMessage, TelegramUpdate};

struct MessageExtractor<'a> {
    message: &'a TelegramMessage,
}

impl<'a> MessageExtractor<'a> {
    fn new(message: &'a TelegramMessage) -> Self {
        Self { message }
    }

    fn chat_id(&self) -> String {
        self.message.chat.as_ref().and_then(|chat| chat.id.as_ref()).map(|id| id.as_string()).unwrap_or_default()
    }

    fn message_id(&self) -> i64 {
        self.message.message_id.unwrap_or(0)
    }

    fn user_id(&self) -> String {
        self.message.from.as_ref().and_then(|from| from.id.as_ref()).map(|id| id.as_string()).unwrap_or_default()
    }

    fn media_group_id(&self) -> Option<String> {
        blank_to_none(self.message.media_group_id.as_deref())
    }

    fn text(&self) -> Option<String> {
        blank_to_none(self.message.text.as_deref()).or_else(|| blank_to_none(self.message.caption.as_deref()))
    }

    fn image_file_ids(&self) -> Vec<String> {
        if let Some(file_id) = self.image_document_file_id() {
            return vec![file_id];
        }
        self.photo_file_id().map(|file_id| vec![file_id]).unwrap_or_default()
    }

    fn reply_to_message(&self) -> Option<MessageExtractor<'a>> {
        self.message.reply_to_message.as_deref().map(MessageExtractor::new)
    }

    fn reply_to_text(&self) -> Option<String> {
        let reply = self.reply_to_message()?;
        if let Some(text) = reply.text() {
            return Some(text);
        }
        if reply.has_photo() {
            return Some(message_constants::REPLY_TO_IMAGE.to_owned());
        }
        if reply.image_document_file_id().is_some() {
            return Some(message_constants::REPLY_TO_IMAGE_DOCUMENT.to_owned());
        }
        None
    }

    fn has_photo(&self) -> bool {
        self.message.photo.as_ref().is_some_and(|photo| !photo.is_empty())
    }

    fn supported(&self) -> bool {
        self.message.text.is_some() || self.has_photo() || self.image_document_file_id().is_some()
    }

    /// Telegram sends several resolutions; the largest one is the useful one.
    fn photo_file_id(&self) -> Option<String> {
        let mut photos: Vec<_> = self.message.photo.as_ref()?.iter().collect();
        photos.sort_by_key(|photo| std::cmp::Reverse(photo.file_size.unwrap_or(0)));
        photos.first()?.file_id.clone().filter(|file_id| !file_id.is_empty())
    }

    fn image_document_file_id(&self) -> Option<String> {
        let document = self.message.document.as_ref()?;
        let file_id = document.file_id.as_deref().filter(|file_id| !file_id.is_empty())?;
        if !is_image_document(document) {
            return None;
        }
        Some(file_id.to_owned())
    }
}

fn is_image_document(document: &TelegramDocument) -> bool {
    let mime_type = document.mime_type.as_deref().unwrap_or("").to_lowercase();
    if IMAGE_MIME_TYPE_PREFIXES.iter().any(|prefix| mime_type.starts_with(prefix)) {
        return true;
    }
    let file_name = document.file_name.as_deref().unwrap_or("").to_lowercase();
    IMAGE_EXTENSIONS.iter().any(|extension| file_name.ends_with(extension))
}

fn blank_to_none(value: Option<&str>) -> Option<String> {
    value.map(|value| value.trim().to_owned()).filter(|value| !value.is_empty())
}

#[derive(Default)]
pub struct TelegramUpdateParser;

impl TelegramUpdateParser {
    pub fn new() -> Self {
        Self
    }

    pub fn parse_incoming_telegram_message(&self, update: Option<&TelegramUpdate>) -> Option<InboundMessage> {
        let update = update?;
        let update_id = update.update_id?;
        let message = update.message.as_ref()?;
        if message.message_id.is_none() || message.from.is_none() || message.chat.is_none() {
            return None;
        }
        let extractor = MessageExtractor::new(message);
        if !extractor.supported() {
            return None;
        }
        let reply = extractor.reply_to_message();
        Some(InboundMessage::new(InboundMessageParams {
            chat_id: extractor.chat_id(),
            image_file_ids: extractor.image_file_ids(),
            media_group_id: extractor.media_group_id(),
            message_id: extractor.message_id(),
            processing_updates: Vec::new(),
            reply_to_image_file_ids: reply.map(|reply| reply.image_file_ids()).unwrap_or_default(),
            reply_to_text: extractor.reply_to_text(),
            text: extractor.text(),
            user_id: extractor.user_id(),
            update_id,
        }))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn parse(payload: serde_json::Value) -> Option<InboundMessage> {
        let update: TelegramUpdate = serde_json::from_value(payload).unwrap();
        TelegramUpdateParser::new().parse_incoming_telegram_message(Some(&update))
    }

    #[test]
    fn parses_text_messages() {
        let message = parse(serde_json::json!({
            "update_id": 99,
            "message": {"message_id": 10, "from": {"id": 5}, "chat": {"id": 3}, "text": "hello"}
        }))
        .unwrap();

        assert_eq!(message.chat_id, "3");
        assert_eq!(message.user_id, "5");
        assert_eq!(message.text.as_deref(), Some("hello"));
    }

    #[test]
    fn uses_largest_photo_and_caption() {
        let message = parse(serde_json::json!({
            "update_id": 99,
            "message": {
                "message_id": 10,
                "from": {"id": 5},
                "chat": {"id": 3},
                "caption": "cap",
                "photo": [{"file_id": "small", "file_size": 1}, {"file_id": "large", "file_size": 9}]
            }
        }))
        .unwrap();

        assert_eq!(message.image_file_ids, vec!["large".to_owned()]);
        assert_eq!(message.text.as_deref(), Some("cap"));
    }

    #[test]
    fn parses_reply_to_image_context() {
        let message = parse(serde_json::json!({
            "update_id": 99,
            "message": {
                "message_id": 10,
                "from": {"id": 5},
                "chat": {"id": 3},
                "text": "reply",
                "reply_to_message": {
                    "message_id": 8,
                    "from": {"id": 5},
                    "chat": {"id": 3},
                    "photo": [{"file_id": "photo", "file_size": 1}]
                }
            }
        }))
        .unwrap();

        assert_eq!(message.reply_to_text.as_deref(), Some(message_constants::REPLY_TO_IMAGE));
        assert_eq!(message.reply_to_image_file_ids, vec!["photo".to_owned()]);
    }

    #[test]
    fn accepts_image_documents_by_extension() {
        let message = parse(serde_json::json!({
            "update_id": 99,
            "message": {
                "message_id": 10,
                "from": {"id": 5},
                "chat": {"id": 3},
                "document": {"file_id": "doc", "file_name": "Photo.HEIC"}
            }
        }))
        .unwrap();

        assert_eq!(message.image_file_ids, vec!["doc".to_owned()]);
    }

    #[test]
    fn rejects_unsupported_and_incomplete_updates() {
        assert!(parse(serde_json::json!({"update_id": 99, "message": {"message_id": 10, "from": {"id": 5}, "chat": {"id": 3}, "voice": {}}})).is_none());
        assert!(parse(serde_json::json!({"message": {"message_id": 10, "from": {"id": 5}, "chat": {"id": 3}, "text": "x"}})).is_none());
        assert!(parse(serde_json::json!({"update_id": 99})).is_none());
    }
}
