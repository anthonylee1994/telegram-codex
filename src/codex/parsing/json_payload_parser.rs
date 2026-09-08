use std::sync::LazyLock;

use anyhow::{Result, bail};
use regex::Regex;
use serde_json::{Value, json};

static FENCE_PREFIX: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"^```(?:json)?\s*").unwrap());
static FENCE_SUFFIX: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"\s*```$").unwrap());
static RELAXED_TEXT: LazyLock<Regex> = LazyLock::new(|| Regex::new(r#"(?s)"text"\s*:\s*"(.*?)"\s*,\s*"suggested_replies"\s*:"#).unwrap());
static RELAXED_SUGGESTED_REPLIES: LazyLock<Regex> = LazyLock::new(|| Regex::new(r#"(?s)"suggested_replies"\s*:\s*\[(.*?)]"#).unwrap());
static QUOTED_STRING: LazyLock<Regex> = LazyLock::new(|| Regex::new(r#"(?s)"((?:\\.|[^"\\])*)""#).unwrap());

/// Tries progressively more forgiving readings of a `codex exec` reply until one
/// of them yields JSON.
#[derive(Default)]
pub struct JsonPayloadParser;

impl JsonPayloadParser {
    pub fn new() -> Self {
        Self
    }

    pub fn parse_payload(&self, raw_reply: Option<&str>) -> Result<Value> {
        for candidate in self.candidate_payloads(raw_reply) {
            if candidate.trim().is_empty() {
                continue;
            }
            let Ok(mut payload) = serde_json::from_str::<Value>(&candidate) else {
                // Keep trying relaxed candidates.
                continue;
            };
            if let Value::String(inner) = &payload {
                match serde_json::from_str::<Value>(inner) {
                    Ok(reparsed) => payload = reparsed,
                    Err(_) => continue,
                }
            }
            if !payload.is_null() {
                return Ok(payload);
            }
        }
        bail!("Reply payload is not JSON")
    }

    fn candidate_payloads(&self, raw_reply: Option<&str>) -> Vec<String> {
        let normalized = raw_reply.unwrap_or("").trim().to_owned();
        let unwrapped = FENCE_SUFFIX.replace(&FENCE_PREFIX.replace(&normalized, ""), "").trim().to_owned();
        let extracted = extract_json_object(&unwrapped);
        let relaxed = extract_relaxed_payload(extracted.as_deref().unwrap_or(&unwrapped));
        vec![normalized, unwrapped, extracted.unwrap_or_default(), relaxed.unwrap_or_default()]
    }
}

fn extract_json_object(text: &str) -> Option<String> {
    let start = text.find('{')?;
    let end = text.rfind('}')?;
    if start < end { Some(text[start..=end].to_owned()) } else { None }
}

fn extract_relaxed_payload(text: &str) -> Option<String> {
    let reply_text = extract_relaxed_text(text);
    let suggested_replies = extract_relaxed_suggested_replies(text);
    if reply_text.is_empty() && suggested_replies.is_empty() {
        return None;
    }
    Some(json!({"text": reply_text, "suggested_replies": suggested_replies}).to_string())
}

fn extract_relaxed_text(text: &str) -> String {
    normalize_text(RELAXED_TEXT.captures(text).and_then(|captures| captures.get(1)).map(|value| value.as_str()))
}

fn extract_relaxed_suggested_replies(text: &str) -> Vec<String> {
    let Some(captures) = RELAXED_SUGGESTED_REPLIES.captures(text) else {
        return Vec::new();
    };
    let inner = captures.get(1).map(|value| value.as_str()).unwrap_or_default();
    if inner.is_empty() {
        return Vec::new();
    }
    QUOTED_STRING.captures_iter(inner).map(|captures| normalize_text(captures.get(1).map(|value| value.as_str()))).collect()
}

/// Turns the literal escape sequences a model often emits into real whitespace.
pub fn normalize_text(value: Option<&str>) -> String {
    value.unwrap_or("").replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\t", "\t").trim().to_owned()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_strict_json() {
        let parser = JsonPayloadParser::new();
        let payload = parser.parse_payload(Some(r#"{"text":"ok"}"#)).unwrap();
        assert_eq!(payload["text"], "ok");
    }

    #[test]
    fn unwraps_fenced_json() {
        let parser = JsonPayloadParser::new();
        let payload = parser.parse_payload(Some("```json\n{\"text\":\"ok\"}\n```")).unwrap();
        assert_eq!(payload["text"], "ok");
    }

    #[test]
    fn falls_back_to_relaxed_extraction() {
        let parser = JsonPayloadParser::new();
        let payload = parser.parse_payload(Some(r#"noise {"text":"hi", "suggested_replies":["a","b","c"] trailing"#)).unwrap();
        assert_eq!(payload["text"], "hi");
        assert_eq!(payload["suggested_replies"].as_array().unwrap().len(), 3);
    }

    #[test]
    fn rejects_non_json() {
        let parser = JsonPayloadParser::new();
        assert!(parser.parse_payload(Some("plain text")).is_err());
    }

    #[test]
    fn normalizes_literal_escapes() {
        assert_eq!(normalize_text(Some("a\\nb\\tc  ")), "a\nb\tc");
    }
}
