use std::sync::{Arc, LazyLock};

use anyhow::{Result, bail};
use regex::Regex;
use serde_json::Value;

use crate::codex::parsing::json_payload_parser::{JsonPayloadParser, normalize_text};
use crate::telegram::shared::message_constants;
use crate::telegram::shared::telegram_types::{MAX_SUGGESTED_REPLIES, MAX_SUGGESTED_REPLY_LENGTH};

static WHITESPACE: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\s+").unwrap());

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ParsedReply {
    pub text: String,
    pub suggested_replies: Vec<String>,
}

pub struct ReplyParser {
    json_payload_parser: Arc<JsonPayloadParser>,
}

impl ReplyParser {
    pub fn new(json_payload_parser: Arc<JsonPayloadParser>) -> Self {
        Self { json_payload_parser }
    }

    pub fn parse_reply(&self, raw_reply: Option<&str>) -> Result<ParsedReply> {
        match self.json_payload_parser.parse_payload(raw_reply) {
            Ok(payload) => Ok(ParsedReply {
                text: self.extract_reply_text(&payload, raw_reply)?,
                suggested_replies: self.extract_suggested_replies(&payload, raw_reply),
            }),
            Err(_) => Ok(ParsedReply {
                text: self.fallback_reply_text(raw_reply)?,
                suggested_replies: sanitize_suggested_replies(&[raw_reply.map(str::to_owned)], message_constants::default_suggested_replies()),
            }),
        }
    }

    fn extract_reply_text(&self, payload: &Value, raw_reply: Option<&str>) -> Result<String> {
        if let Value::Object(map) = payload {
            if let Some(Value::String(text)) = map.get("text")
                && !text.trim().is_empty()
            {
                return Ok(normalize_text(Some(text)));
            }
            let mut candidates: Vec<&str> = map.values().filter_map(Value::as_str).filter(|value| !value.trim().is_empty()).collect();
            candidates.sort_unstable();
            if let Some(candidate) = candidates.last() {
                return Ok(normalize_text(Some(candidate)));
            }
        }
        if let Value::String(text) = payload
            && !text.trim().is_empty()
        {
            return Ok(normalize_text(Some(text)));
        }
        self.fallback_reply_text(raw_reply)
    }

    fn fallback_reply_text(&self, raw_reply: Option<&str>) -> Result<String> {
        let normalized = normalize_text(raw_reply);
        if normalized.is_empty() {
            bail!("codex exec returned an empty reply");
        }
        Ok(normalized)
    }

    fn extract_suggested_replies(&self, payload: &Value, raw_reply: Option<&str>) -> Vec<String> {
        let fallback = message_constants::default_suggested_replies();
        if let Value::Array(values) = payload {
            return sanitize_suggested_replies(&as_optional_strings(values), fallback);
        }
        if let Value::Object(map) = payload
            && let Some(Value::Array(values)) = map.get("suggested_replies")
        {
            return sanitize_suggested_replies(&as_optional_strings(values), fallback);
        }
        sanitize_suggested_replies(&[raw_reply.map(str::to_owned)], fallback)
    }
}

/// Keeps at most three unique, whitespace-collapsed replies and falls back when
/// the model produced fewer than the required amount.
pub fn sanitize_suggested_replies(replies: &[Option<String>], fallback: Vec<String>) -> Vec<String> {
    let mut cleaned: Vec<String> = Vec::new();
    for reply in replies {
        let Some(reply) = reply else {
            continue;
        };
        let normalized = WHITESPACE.replace_all(reply.trim(), " ").into_owned();
        if !normalized.is_empty() && !cleaned.contains(&normalized) {
            cleaned.push(truncate_chars(&normalized, MAX_SUGGESTED_REPLY_LENGTH));
        }
        if cleaned.len() == MAX_SUGGESTED_REPLIES {
            break;
        }
    }
    if cleaned.len() < MAX_SUGGESTED_REPLIES { fallback } else { cleaned }
}

fn as_optional_strings(values: &[Value]) -> Vec<Option<String>> {
    values.iter().map(|value| value.as_str().map(str::to_owned)).collect()
}

fn truncate_chars(value: &str, max_chars: usize) -> String {
    if value.chars().count() <= max_chars {
        value.to_owned()
    } else {
        value.chars().take(max_chars).collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn parser() -> ReplyParser {
        ReplyParser::new(Arc::new(JsonPayloadParser::new()))
    }

    #[test]
    fn parses_strict_json_replies() {
        let parsed = parser().parse_reply(Some(r#"{"text":"ok","suggested_replies":["a","b","c"]}"#)).unwrap();
        assert_eq!(
            parsed,
            ParsedReply {
                text: "ok".to_owned(),
                suggested_replies: vec!["a".to_owned(), "b".to_owned(), "c".to_owned()],
            }
        );
    }

    #[test]
    fn falls_back_to_default_suggested_replies() {
        let parsed = parser().parse_reply(Some("plain text")).unwrap();
        assert_eq!(parsed.text, "plain text");
        assert_eq!(parsed.suggested_replies.len(), 3);
    }

    #[test]
    fn rejects_empty_replies() {
        assert!(parser().parse_reply(Some("   ")).is_err());
    }
}
