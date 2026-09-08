use std::sync::{Arc, LazyLock};

use anyhow::Result;
use regex::Regex;
use serde::Serialize;

use crate::codex::parsing::reply_parser::{ParsedReply, ReplyParser};
use crate::telegram::shared::telegram_types::MAX_SUGGESTED_REPLIES;

static FENCED_CODE_BLOCK: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"(?s)```(?:[\t ]*[\w#+.\-]+)?\n?(.*?)```").unwrap());

#[derive(Clone, Debug, Eq, PartialEq, Serialize)]
pub struct KeyboardButton {
    pub text: String,
}

#[derive(Clone, Debug, Default, Eq, PartialEq, Serialize)]
pub struct TelegramReplyMarkup {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub remove_keyboard: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub keyboard: Option<Vec<Vec<KeyboardButton>>>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub resize_keyboard: Option<bool>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub one_time_keyboard: Option<bool>,
}

/// Inline markdown rule. The regex crate has no lookbehind, so `__italic__`
/// carries an explicit "must not touch another underscore" flag instead.
struct InlineRule {
    delimiter: &'static str,
    forbidden: char,
    tag: &'static str,
    no_adjacent_underscore: bool,
}

const INLINE_RULES: &[InlineRule] = &[
    InlineRule {
        delimiter: "`",
        forbidden: '`',
        tag: "code",
        no_adjacent_underscore: false,
    },
    InlineRule {
        delimiter: "**",
        forbidden: '*',
        tag: "b",
        no_adjacent_underscore: false,
    },
    InlineRule {
        delimiter: "||",
        forbidden: '|',
        tag: "tg-spoiler",
        no_adjacent_underscore: false,
    },
    InlineRule {
        delimiter: "~~",
        forbidden: '~',
        tag: "s",
        no_adjacent_underscore: false,
    },
    InlineRule {
        delimiter: "__",
        forbidden: '_',
        tag: "i",
        no_adjacent_underscore: true,
    },
];

pub struct TelegramMessageFormatter {
    reply_parser: Option<Arc<ReplyParser>>,
}

impl TelegramMessageFormatter {
    pub fn new(reply_parser: Option<Arc<ReplyParser>>) -> Self {
        Self { reply_parser }
    }

    pub fn format_for_telegram(&self, text: Option<&str>) -> String {
        let Some(text) = text.filter(|value| !value.is_empty()) else {
            return String::new();
        };
        let mut cursor = 0;
        let mut formatted = String::new();
        for captures in FENCED_CODE_BLOCK.captures_iter(text) {
            let whole = captures.get(0).unwrap();
            formatted.push_str(&format_inline_segment(&text[cursor..whole.start()]));
            let body = captures.get(1).map(|value| value.as_str()).unwrap_or("");
            formatted.push_str(&format!("<pre><code>{}</code></pre>", escape_html(strip_single_leading_newline(body))));
            cursor = whole.end();
        }
        formatted.push_str(&format_inline_segment(&text[cursor..]));
        formatted
    }

    pub fn build_reply_markup(&self, suggested_replies: &[String], remove_keyboard: bool) -> Option<TelegramReplyMarkup> {
        if remove_keyboard {
            return Some(TelegramReplyMarkup {
                remove_keyboard: Some(true),
                ..Default::default()
            });
        }
        let replies = clean_replies(suggested_replies);
        if replies.is_empty() {
            return None;
        }
        Some(TelegramReplyMarkup {
            keyboard: Some(replies.into_iter().map(|reply| vec![KeyboardButton { text: reply }]).collect()),
            resize_keyboard: Some(true),
            one_time_keyboard: Some(true),
            ..Default::default()
        })
    }

    pub fn normalize_reply(&self, text: Option<&str>, suggested_replies: &[String]) -> Result<ParsedReply> {
        let payload = match &self.reply_parser {
            Some(reply_parser) => reply_parser.parse_reply(text)?,
            None => ParsedReply {
                text: text.unwrap_or("").to_owned(),
                suggested_replies: Vec::new(),
            },
        };
        Ok(ParsedReply {
            text: if payload.text.trim().is_empty() { text.unwrap_or("").to_owned() } else { payload.text },
            suggested_replies: if suggested_replies.is_empty() { payload.suggested_replies } else { suggested_replies.to_vec() },
        })
    }
}

fn clean_replies(replies: &[String]) -> Vec<String> {
    let mut cleaned: Vec<String> = Vec::new();
    for reply in replies {
        let normalized = reply.trim();
        if !normalized.is_empty() && !cleaned.iter().any(|existing| existing == normalized) {
            cleaned.push(normalized.to_owned());
        }
        if cleaned.len() == MAX_SUGGESTED_REPLIES {
            break;
        }
    }
    cleaned
}

fn escape_html(text: &str) -> String {
    text.replace('&', "&amp;").replace('<', "&lt;").replace('>', "&gt;")
}

fn strip_single_leading_newline(text: &str) -> &str {
    if let Some(stripped) = text.strip_prefix("\r\n") {
        return stripped;
    }
    text.strip_prefix('\n').unwrap_or(text)
}

fn format_inline_segment(text: &str) -> String {
    apply_rules(text, 0)
}

/// Applies inline rules in priority order; text between matches recurses into
/// the remaining, lower priority rules.
fn apply_rules(text: &str, rule_index: usize) -> String {
    let Some(rule) = INLINE_RULES.get(rule_index) else {
        return escape_html(text);
    };
    let mut formatted = String::new();
    let mut cursor = 0;
    let mut search_from = 0;
    while let Some(found) = find_match(text, rule, search_from) {
        formatted.push_str(&apply_rules(&text[cursor..found.start], rule_index + 1));
        formatted.push_str(&wrap(rule.tag, &text[found.content_start..found.content_end]));
        cursor = found.end;
        search_from = found.end;
    }
    formatted.push_str(&apply_rules(&text[cursor..], rule_index + 1));
    formatted
}

struct InlineMatch {
    start: usize,
    content_start: usize,
    content_end: usize,
    end: usize,
}

fn find_match(text: &str, rule: &InlineRule, search_from: usize) -> Option<InlineMatch> {
    let delimiter_len = rule.delimiter.len();
    let mut index = search_from;
    while index < text.len() {
        if !text.is_char_boundary(index) {
            index += 1;
            continue;
        }
        if !text[index..].starts_with(rule.delimiter) || (rule.no_adjacent_underscore && preceded_by_underscore(text, index)) {
            index += next_char_len(text, index);
            continue;
        }
        let content_start = index + delimiter_len;
        if let Some(content_end) = scan_content(text, rule, content_start) {
            let end = content_end + delimiter_len;
            if !(rule.no_adjacent_underscore && text[end..].starts_with('_')) {
                return Some(InlineMatch {
                    start: index,
                    content_start,
                    content_end,
                    end,
                });
            }
        }
        index += next_char_len(text, index);
    }
    None
}

/// Walks forward until the closing delimiter, refusing forbidden characters and
/// newlines so a rule can never span lines.
fn scan_content(text: &str, rule: &InlineRule, content_start: usize) -> Option<usize> {
    let mut index = content_start;
    while index < text.len() {
        if text[index..].starts_with(rule.delimiter) && index > content_start {
            return Some(index);
        }
        let character = text[index..].chars().next()?;
        if character == rule.forbidden || character == '\n' {
            return None;
        }
        index += character.len_utf8();
    }
    None
}

fn preceded_by_underscore(text: &str, index: usize) -> bool {
    text[..index].ends_with('_')
}

fn next_char_len(text: &str, index: usize) -> usize {
    text[index..].chars().next().map(char::len_utf8).unwrap_or(1)
}

fn wrap(tag: &str, content: &str) -> String {
    format!("<{tag}>{}</{tag}>", escape_html(content))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn formatter() -> TelegramMessageFormatter {
        TelegramMessageFormatter::new(None)
    }

    #[test]
    fn escapes_html_and_formats_inline_markdown() {
        assert_eq!(formatter().format_for_telegram(Some("**hi** <x> `code`")), "<b>hi</b> &lt;x&gt; <code>code</code>");
    }

    #[test]
    fn formats_fenced_code_blocks() {
        assert_eq!(formatter().format_for_telegram(Some("```ts\nconst a = 1 < 2\n```")), "<pre><code>const a = 1 &lt; 2\n</code></pre>");
    }

    #[test]
    fn formats_remaining_inline_rules() {
        assert_eq!(formatter().format_for_telegram(Some("||s|| ~~d~~ __i__")), "<tg-spoiler>s</tg-spoiler> <s>d</s> <i>i</i>");
    }

    #[test]
    fn ignores_unterminated_and_multiline_delimiters() {
        assert_eq!(formatter().format_for_telegram(Some("**a\nb**")), "**a\nb**");
        assert_eq!(formatter().format_for_telegram(Some("****")), "****");
    }

    #[test]
    fn skips_italic_touching_extra_underscores() {
        assert_eq!(formatter().format_for_telegram(Some("___i___")), "___i___");
    }

    #[test]
    fn cleans_suggested_replies() {
        let markup = formatter().build_reply_markup(&[" a ".to_owned(), "a".to_owned(), "b".to_owned()], false).unwrap();
        assert_eq!(
            markup,
            TelegramReplyMarkup {
                remove_keyboard: None,
                keyboard: Some(vec![vec![KeyboardButton { text: "a".to_owned() }], vec![KeyboardButton { text: "b".to_owned() }]]),
                resize_keyboard: Some(true),
                one_time_keyboard: Some(true),
            }
        );
    }

    #[test]
    fn removes_keyboard_when_requested() {
        let markup = formatter().build_reply_markup(&[], true).unwrap();
        assert_eq!(markup.remove_keyboard, Some(true));
        assert!(markup.keyboard.is_none());
    }

    #[test]
    fn returns_no_markup_without_replies() {
        assert!(formatter().build_reply_markup(&[], false).is_none());
    }
}
