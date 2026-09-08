use anyhow::{Result, bail};

use crate::telegram::shared::inbound_message::{InboundMessage, ProcessingUpdate};

/// Collapses the individual photos of a Telegram album into one message.
#[derive(Default)]
pub struct MediaGroupMerger;

impl MediaGroupMerger {
    pub fn new() -> Self {
        Self
    }

    pub fn merge(&self, messages: Vec<InboundMessage>) -> Result<InboundMessage> {
        if messages.is_empty() {
            bail!("Cannot merge empty message list");
        }
        let mut sorted = messages;
        sorted.sort_by(|left, right| left.message_id.cmp(&right.message_id).then(left.update_id.cmp(&right.update_id)));

        let text = sorted.iter().find_map(|message| message.text.clone().filter(|text| !text.trim().is_empty()));
        let mut image_file_ids: Vec<String> = Vec::new();
        for message in &sorted {
            for image_file_id in &message.image_file_ids {
                if !image_file_ids.contains(image_file_id) {
                    image_file_ids.push(image_file_id.clone());
                }
            }
        }
        let processing_updates: Vec<ProcessingUpdate> = sorted
            .iter()
            .map(|message| ProcessingUpdate {
                update_id: message.update_id,
                message_id: message.message_id,
            })
            .collect();

        Ok(InboundMessage::for_merged_media_group(&sorted[0], image_file_ids, processing_updates, text))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::telegram::shared::inbound_message::InboundMessageParams;

    fn message(update_id: i64, message_id: i64, text: Option<&str>, file_id: &str) -> InboundMessage {
        InboundMessage::new(InboundMessageParams {
            chat_id: "3".to_owned(),
            image_file_ids: vec![file_id.to_owned()],
            media_group_id: Some("group".to_owned()),
            message_id,
            text: text.map(str::to_owned),
            user_id: "5".to_owned(),
            update_id,
            ..Default::default()
        })
    }

    #[test]
    fn merges_in_message_order_and_keeps_first_caption() {
        let merged = MediaGroupMerger::new().merge(vec![message(2, 12, Some("second"), "b"), message(1, 11, Some("first"), "a")]).unwrap();

        assert_eq!(merged.image_file_ids, vec!["a".to_owned(), "b".to_owned()]);
        assert_eq!(merged.text.as_deref(), Some("first"));
        assert_eq!(merged.message_id, 11);
        assert_eq!(merged.processing_updates.len(), 2);
    }

    #[test]
    fn rejects_empty_input() {
        assert!(MediaGroupMerger::new().merge(Vec::new()).is_err());
    }
}
