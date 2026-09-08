use serde::{Deserialize, Serialize};

use crate::telegram::shared::message_constants;

const MAX_TRANSCRIPT_MESSAGES: usize = 100;

#[derive(Clone, Debug, Eq, PartialEq, Serialize, Deserialize)]
pub struct TranscriptEntry {
    pub role: String,
    pub content: String,
}

/// Immutable rolling window over the last 100 conversation messages.
#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct Transcript {
    messages: Vec<TranscriptEntry>,
}

impl Transcript {
    fn from_messages(messages: Vec<TranscriptEntry>) -> Self {
        let messages = if messages.len() <= MAX_TRANSCRIPT_MESSAGES {
            messages
        } else {
            messages[messages.len() - MAX_TRANSCRIPT_MESSAGES..].to_vec()
        };
        Self { messages }
    }

    pub fn empty() -> Self {
        Self::from_messages(Vec::new())
    }

    pub fn from_conversation_state(conversation_state: Option<&str>) -> Self {
        let Some(conversation_state) = conversation_state.filter(|value| !value.trim().is_empty()) else {
            return Self::empty();
        };
        match serde_json::from_str::<Vec<TranscriptEntry>>(conversation_state) {
            Ok(payload) => Self::from_messages(
                payload
                    .into_iter()
                    .filter(|entry| entry.role == "user" || entry.role == "assistant")
                    .filter(|entry| !entry.content.trim().is_empty())
                    .collect(),
            ),
            Err(_) => Self::empty(),
        }
    }

    pub fn append(&self, role: Option<&str>, content: Option<&str>) -> Self {
        let mut messages = self.messages.clone();
        messages.push(TranscriptEntry {
            role: role.unwrap_or("").to_owned(),
            content: content.unwrap_or("").to_owned(),
        });
        Self::from_messages(messages)
    }

    pub fn size(&self) -> usize {
        self.messages.len()
    }

    pub fn to_tagged_prompt_lines(&self) -> Vec<String> {
        self.messages
            .iter()
            .enumerate()
            .flat_map(|(index, message)| [format!(r#"<message index="{}" role="{}">"#, index + 1, message.role), message.content.clone(), "</message>".to_owned()])
            .collect()
    }

    pub fn to_conversation_state(&self) -> String {
        serde_json::to_string(&self.messages).unwrap_or_else(|_| "[]".to_owned())
    }

    pub fn compact_baseline(compact_text: &str) -> Self {
        Self::empty()
            .append(Some("user"), Some(message_constants::COMPACT_BASELINE_MESSAGE))
            .append(Some("assistant"), Some(compact_text))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn drops_unknown_roles_and_blank_content() {
        let transcript = Transcript::from_conversation_state(Some(r#"[{"role":"user","content":"a"},{"role":"system","content":"b"},{"role":"assistant","content":"  "}]"#));
        assert_eq!(transcript.size(), 1);
    }

    #[test]
    fn returns_empty_on_invalid_json() {
        assert_eq!(Transcript::from_conversation_state(Some("not json")).size(), 0);
        assert_eq!(Transcript::from_conversation_state(None).size(), 0);
    }

    #[test]
    fn keeps_only_the_last_hundred_messages() {
        let mut transcript = Transcript::empty();
        for index in 0..150 {
            transcript = transcript.append(Some("user"), Some(&index.to_string()));
        }
        assert_eq!(transcript.size(), MAX_TRANSCRIPT_MESSAGES);
        assert!(transcript.to_conversation_state().contains("\"149\""));
        assert!(!transcript.to_conversation_state().contains("\"49\""));
    }

    #[test]
    fn renders_tagged_prompt_lines() {
        let transcript = Transcript::empty().append(Some("user"), Some("hi"));
        assert_eq!(
            transcript.to_tagged_prompt_lines(),
            vec![r#"<message index="1" role="user">"#.to_owned(), "hi".to_owned(), "</message>".to_owned()]
        );
    }
}
