use std::path::PathBuf;
use std::sync::Arc;

use anyhow::Result;
use serde_json::{Value, json};

use crate::codex::execution::exec_runner::ExecRunner;
use crate::codex::parsing::reply_parser::ReplyParser;
use crate::codex::reply::prompt_builder::PromptBuilder;
use crate::codex::shared::transcript::Transcript;
use crate::conversation::reply::reply_result::ReplyResult;
use crate::telegram::shared::telegram_types::MAX_SUGGESTED_REPLIES;

pub struct CodexReplyClient {
    exec_runner: Arc<ExecRunner>,
    prompt_builder: Arc<PromptBuilder>,
    reply_parser: Arc<ReplyParser>,
}

impl CodexReplyClient {
    pub fn new(exec_runner: Arc<ExecRunner>, prompt_builder: Arc<PromptBuilder>, reply_parser: Arc<ReplyParser>) -> Self {
        Self {
            exec_runner,
            prompt_builder,
            reply_parser,
        }
    }

    pub async fn generate_reply(
        &self,
        user_message: Option<&str>,
        conversation_state: Option<&str>,
        image_file_paths: &[PathBuf],
        reply_to_text: Option<&str>,
        long_term_memory: Option<&str>,
    ) -> Result<ReplyResult> {
        let next_transcript = self.append_user_message(conversation_state, user_message, image_file_paths, reply_to_text);
        let raw_reply = self
            .exec_runner
            .run_with_system(
                Some(&self.prompt_builder.build_reply_system_prompt()),
                Some(
                    &self
                        .prompt_builder
                        .build_reply_user_prompt(&next_transcript, !image_file_paths.is_empty(), image_file_paths.len(), long_term_memory),
                ),
                image_file_paths,
                Some(&reply_output_schema()),
            )
            .await?;
        let parsed_reply = self.reply_parser.parse_reply(Some(&raw_reply))?;
        Ok(ReplyResult {
            conversation_state: Some(next_transcript.append(Some("assistant"), Some(&parsed_reply.text)).to_conversation_state()),
            suggested_replies: parsed_reply.suggested_replies,
            text: parsed_reply.text,
        })
    }

    fn append_user_message(&self, conversation_state: Option<&str>, text: Option<&str>, image_file_paths: &[PathBuf], reply_to_text: Option<&str>) -> Transcript {
        let user_message = build_user_message(text, image_file_paths, reply_to_text);
        Transcript::from_conversation_state(conversation_state).append(Some("user"), Some(&user_message))
    }
}

fn build_user_message(text: Option<&str>, image_file_paths: &[PathBuf], reply_to_text: Option<&str>) -> String {
    let base_text = normalize_user_text(text, image_file_paths);
    let Some(reply_to_text) = reply_to_text.filter(|value| !value.trim().is_empty()) else {
        return base_text;
    };
    let new_message = if base_text.trim().is_empty() { "（冇文字）" } else { base_text.as_str() };
    [
        "你而家係回覆緊之前一則訊息。".to_owned(),
        format!("被引用訊息：{reply_to_text}"),
        format!("你今次嘅新訊息：{new_message}"),
    ]
    .join("\n")
}

fn normalize_user_text(text: Option<&str>, image_file_paths: &[PathBuf]) -> String {
    let base_text = text.unwrap_or("");
    if !base_text.trim().is_empty() || image_file_paths.is_empty() {
        return base_text.to_owned();
    }
    if image_file_paths.len() == 1 {
        return "我上載咗張圖。請先描述圖片，再按內容幫我分析重點。".to_owned();
    }
    let labels = (1..=image_file_paths.len()).map(|index| format!("圖 {index}")).collect::<Vec<_>>().join("、");
    format!("我上載咗 {} 張圖。請按 {labels} 逐張描述，再比較異同同整理重點。", image_file_paths.len())
}

fn reply_output_schema() -> Value {
    json!({
        "type": "object",
        "additionalProperties": false,
        "required": ["text", "suggested_replies"],
        "properties": {
            "text": {"type": "string", "minLength": 1},
            "suggested_replies": {
                "type": "array",
                "minItems": MAX_SUGGESTED_REPLIES,
                "maxItems": MAX_SUGGESTED_REPLIES,
                "items": {"type": "string", "minLength": 1}
            }
        }
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn prompts_for_single_image_without_caption() {
        assert_eq!(normalize_user_text(None, &[PathBuf::from("a.png")]), "我上載咗張圖。請先描述圖片，再按內容幫我分析重點。");
    }

    #[test]
    fn prompts_for_album_without_caption() {
        let message = normalize_user_text(Some("  "), &[PathBuf::from("a.png"), PathBuf::from("b.png")]);
        assert!(message.contains("我上載咗 2 張圖"));
        assert!(message.contains("圖 1、圖 2"));
    }

    #[test]
    fn wraps_quoted_message_context() {
        let message = build_user_message(Some("new"), &[], Some("quoted"));
        assert!(message.contains("被引用訊息：quoted"));
        assert!(message.contains("你今次嘅新訊息：new"));
    }
}
