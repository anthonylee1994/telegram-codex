use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::sync::Arc;
use std::time::Duration;

use async_trait::async_trait;
use serde_json::Value;
use thiserror::Error;
use tokio::io::AsyncWriteExt;
use tokio::process::Command;

use crate::config::AppConfig;

#[derive(Debug, Error)]
pub enum ExecutionError {
    #[error("{0}")]
    Failed(String),
    #[error("{0}")]
    TimedOut(String),
}

/// `codex exec` writes its reply to `--output-last-message`, so its stdout is
/// discarded instead of buffered. Only the tail of stderr is kept, which is all
/// the failure message needs.
const MAX_CAPTURED_STDERR_BYTES: usize = 8 * 1024;

#[derive(Clone, Debug)]
pub struct ProcessResult {
    pub exit_code: i32,
    pub stderr: String,
    pub timed_out: bool,
}

/// Seam that lets tests observe the assembled command without spawning `codex`.
#[async_trait]
pub trait ProcessSpawner: Send + Sync {
    async fn execute_command(&self, command: &[String], working_directory: &Path, prompt: Option<&str>, timeout_seconds: u64) -> anyhow::Result<ProcessResult>;
}

pub struct TokioProcessSpawner;

#[async_trait]
impl ProcessSpawner for TokioProcessSpawner {
    async fn execute_command(&self, command: &[String], working_directory: &Path, prompt: Option<&str>, timeout_seconds: u64) -> anyhow::Result<ProcessResult> {
        let mut child = Command::new(&command[0])
            .args(&command[1..])
            .current_dir(working_directory)
            .stdin(Stdio::piped())
            .stdout(Stdio::null())
            .stderr(Stdio::piped())
            .kill_on_drop(true)
            .spawn()?;

        if let Some(mut stdin) = child.stdin.take() {
            if let Some(prompt) = prompt {
                stdin.write_all(prompt.as_bytes()).await?;
            }
            stdin.shutdown().await.ok();
        }

        match tokio::time::timeout(Duration::from_secs(timeout_seconds), child.wait_with_output()).await {
            Ok(output) => {
                let output = output?;
                Ok(ProcessResult {
                    exit_code: output.status.code().unwrap_or(-1),
                    stderr: into_captured_stderr(output.stderr),
                    timed_out: false,
                })
            }
            Err(_) => Ok(ProcessResult {
                exit_code: -1,
                stderr: String::new(),
                timed_out: true,
            }),
        }
    }
}

/// Keeps only the tail of stderr and reuses the captured buffer, so a chatty
/// failure cannot pull megabytes of log text onto the heap.
fn into_captured_stderr(bytes: Vec<u8>) -> String {
    let mut bytes = bytes;
    if bytes.len() > MAX_CAPTURED_STDERR_BYTES {
        bytes = bytes.split_off(bytes.len() - MAX_CAPTURED_STDERR_BYTES);
    }
    match String::from_utf8(bytes) {
        Ok(text) => text,
        // Splitting the tail can land inside a multi byte character.
        Err(error) => String::from_utf8_lossy(error.as_bytes()).into_owned(),
    }
}

/// Wraps a single `codex exec` invocation: temp workspace, optional output
/// schema, stdin prompt, and reading back `--output-last-message`.
pub struct ExecRunner {
    config: Arc<AppConfig>,
    spawner: Arc<dyn ProcessSpawner>,
}

impl ExecRunner {
    pub fn new(config: Arc<AppConfig>) -> Self {
        Self::with_spawner(config, Arc::new(TokioProcessSpawner))
    }

    pub fn with_spawner(config: Arc<AppConfig>, spawner: Arc<dyn ProcessSpawner>) -> Self {
        Self { config, spawner }
    }

    pub async fn run(&self, prompt: Option<&str>, image_file_paths: &[PathBuf], output_schema: Option<&Value>) -> Result<String, ExecutionError> {
        self.run_with_system(None, prompt, image_file_paths, output_schema).await
    }

    pub async fn run_with_system(&self, system_prompt: Option<&str>, user_prompt: Option<&str>, image_file_paths: &[PathBuf], output_schema: Option<&Value>) -> Result<String, ExecutionError> {
        match self.run_inner(system_prompt, user_prompt, image_file_paths, output_schema).await {
            Ok(reply) => Ok(reply),
            Err(error) => match error.downcast::<ExecutionError>() {
                Ok(execution_error) => Err(execution_error),
                Err(_) => Err(ExecutionError::Failed("Failed to run codex exec".to_owned())),
            },
        }
    }

    async fn run_inner(&self, system_prompt: Option<&str>, user_prompt: Option<&str>, image_file_paths: &[PathBuf], output_schema: Option<&Value>) -> anyhow::Result<String> {
        let temp_dir = tempfile::Builder::new().prefix("telegram-codex-").tempdir()?;
        let output_path = temp_dir.path().join("reply.txt");
        let schema_path = match output_schema {
            Some(schema) => {
                let schema_path = temp_dir.path().join("reply-schema.json");
                tokio::fs::write(&schema_path, serde_json::to_string(schema)?).await?;
                Some(schema_path)
            }
            None => None,
        };

        let command = self.build_command(&output_path, schema_path.as_deref(), image_file_paths);
        let prompt = build_prompt(system_prompt, user_prompt);
        let result = self
            .spawner
            .execute_command(&command, temp_dir.path(), prompt.as_deref(), self.config.codex_exec_timeout_seconds)
            .await?;

        if result.timed_out {
            return Err(ExecutionError::TimedOut(format!("codex exec timed out after {} seconds", self.config.codex_exec_timeout_seconds)).into());
        }
        if result.exit_code != 0 {
            let detail = result.stderr.trim();
            let detail = if detail.is_empty() { "unknown error" } else { detail };
            return Err(ExecutionError::Failed(format!("codex exec failed: {detail}")).into());
        }

        let reply_text = tokio::fs::read_to_string(&output_path)
            .await
            .map_err(|_| ExecutionError::Failed("codex exec returned an empty reply".to_owned()))?;
        let reply_text = reply_text.trim().to_owned();
        if reply_text.is_empty() {
            return Err(ExecutionError::Failed("codex exec returned an empty reply".to_owned()).into());
        }
        Ok(reply_text)
    }

    fn build_command(&self, output_path: &Path, schema_path: Option<&Path>, image_file_paths: &[PathBuf]) -> Vec<String> {
        let mut command: Vec<String> = [
            "codex",
            "exec",
            "--skip-git-repo-check",
            "--sandbox",
            &self.config.codex_sandbox_mode,
            "--color",
            "never",
            "--output-last-message",
        ]
        .iter()
        .map(|argument| (*argument).to_owned())
        .collect();
        command.push(output_path.to_string_lossy().into_owned());
        if let Some(schema_path) = schema_path {
            command.push("--output-schema".to_owned());
            command.push(schema_path.to_string_lossy().into_owned());
        }
        for image_file_path in image_file_paths {
            command.push("--image".to_owned());
            command.push(image_file_path.to_string_lossy().into_owned());
        }
        command.push("-".to_owned());
        command
    }
}

fn build_prompt(system_prompt: Option<&str>, user_prompt: Option<&str>) -> Option<String> {
    let system_prompt = system_prompt.filter(|value| !value.trim().is_empty());
    let Some(system_prompt) = system_prompt else {
        return user_prompt.map(str::to_owned);
    };
    let Some(user_prompt) = user_prompt.filter(|value| !value.trim().is_empty()) else {
        return Some(system_prompt.to_owned());
    };
    Some(["<system_prompt>", system_prompt, "</system_prompt>", "<user_prompt>", user_prompt, "</user_prompt>"].join("\n\n"))
}

#[cfg(test)]
mod tests {
    use std::sync::Mutex;

    use serde_json::json;

    use super::*;

    #[derive(Clone)]
    struct CapturedInvocation {
        command: Vec<String>,
        working_directory: PathBuf,
        prompt: Option<String>,
    }

    impl CapturedInvocation {
        fn output_path(&self) -> PathBuf {
            PathBuf::from(&self.command[self.command.iter().position(|argument| argument == "--output-last-message").unwrap() + 1])
        }
    }

    #[derive(Default)]
    struct CapturingSpawner {
        captured: Mutex<Option<CapturedInvocation>>,
    }

    #[async_trait]
    impl ProcessSpawner for CapturingSpawner {
        async fn execute_command(&self, command: &[String], working_directory: &Path, prompt: Option<&str>, _timeout_seconds: u64) -> anyhow::Result<ProcessResult> {
            let output_path = PathBuf::from(&command[command.iter().position(|argument| argument == "--output-last-message").unwrap() + 1]);
            tokio::fs::write(&output_path, r#"{"text":"ok","suggested_replies":["a","b","c"]}"#).await?;
            *self.captured.lock().unwrap() = Some(CapturedInvocation {
                command: command.to_vec(),
                working_directory: working_directory.to_path_buf(),
                prompt: prompt.map(str::to_owned),
            });
            Ok(ProcessResult {
                exit_code: 0,
                stderr: String::new(),
                timed_out: false,
            })
        }
    }

    fn config() -> Arc<AppConfig> {
        Arc::new(AppConfig {
            port: 3000,
            base_url: "https://example.com".to_owned(),
            telegram_bot_token: "token".to_owned(),
            telegram_webhook_secret: "secret".to_owned(),
            allowed_telegram_user_ids: Vec::new(),
            sqlite_db_path: PathBuf::from("./data/app.db"),
            codex_exec_timeout_seconds: 300,
            max_media_group_images: 10,
            session_ttl_days: 7,
            media_group_wait_ms: 1200,
            rate_limit_window_ms: 10000,
            rate_limit_max_messages: 5,
            codex_sandbox_mode: "danger-full-access".to_owned(),
        })
    }

    #[test]
    fn keeps_only_the_stderr_tail() {
        assert_eq!(into_captured_stderr(b"short".to_vec()), "short");

        let mut noisy = vec![b'a'; MAX_CAPTURED_STDERR_BYTES];
        noisy.extend_from_slice(b"the real error");
        let captured = into_captured_stderr(noisy);
        assert_eq!(captured.len(), MAX_CAPTURED_STDERR_BYTES);
        assert!(captured.ends_with("the real error"));
    }

    #[test]
    fn tolerates_a_split_multi_byte_character() {
        let mut noisy = "屌".repeat(MAX_CAPTURED_STDERR_BYTES).into_bytes();
        noisy.extend_from_slice(b"tail");
        assert!(into_captured_stderr(noisy).ends_with("tail"));
    }

    #[tokio::test]
    async fn uses_temp_directory_as_working_directory() {
        let spawner = Arc::new(CapturingSpawner::default());
        let exec_runner = ExecRunner::with_spawner(config(), spawner.clone());

        let reply = exec_runner.run(Some("prompt"), &[], Some(&json!({"type": "object"}))).await.unwrap();

        assert_eq!(reply, r#"{"text":"ok","suggested_replies":["a","b","c"]}"#);
        let captured = spawner.captured.lock().unwrap().clone().unwrap();
        assert_eq!(captured.output_path().parent().unwrap(), captured.working_directory.as_path());
        // The temp directory is already removed here, so compare paths only.
        assert!(captured.working_directory.is_absolute());
        assert_ne!(captured.working_directory, std::env::current_dir().unwrap());
    }

    #[tokio::test]
    async fn wraps_system_and_user_prompts() {
        let spawner = Arc::new(CapturingSpawner::default());
        let exec_runner = ExecRunner::with_spawner(config(), spawner.clone());

        exec_runner
            .run_with_system(Some("system rules"), Some("user payload"), &[], Some(&json!({"type": "object"})))
            .await
            .unwrap();

        let prompt = spawner.captured.lock().unwrap().clone().unwrap().prompt.unwrap();
        assert!(prompt.contains("<system_prompt>"));
        assert!(prompt.contains("system rules"));
        assert!(prompt.contains("<user_prompt>"));
        assert!(prompt.contains("user payload"));
    }

    #[tokio::test]
    async fn appends_image_arguments_and_schema() {
        let spawner = Arc::new(CapturingSpawner::default());
        let exec_runner = ExecRunner::with_spawner(config(), spawner.clone());

        exec_runner.run(Some("prompt"), &[PathBuf::from("/tmp/a.png")], Some(&json!({"type": "object"}))).await.unwrap();

        let command = spawner.captured.lock().unwrap().clone().unwrap().command;
        assert!(command.contains(&"--output-schema".to_owned()));
        assert!(command.contains(&"--image".to_owned()));
        assert!(command.contains(&"/tmp/a.png".to_owned()));
        assert_eq!(command.last().unwrap(), "-");
    }
}
