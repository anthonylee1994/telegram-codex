use std::collections::HashSet;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use anyhow::{Result, anyhow};
use tracing::warn;

use crate::telegram::shared::telegram_types::TelegramGateway;

pub struct AttachmentDownloader {
    telegram_client: Arc<dyn TelegramGateway>,
}

impl AttachmentDownloader {
    pub fn new(telegram_client: Arc<dyn TelegramGateway>) -> Self {
        Self { telegram_client }
    }

    /// Downloads every image concurrently while preserving album order, which
    /// the prompts rely on ("圖 1", "圖 2", …).
    pub async fn download_images(&self, image_file_ids: &[String]) -> Result<Vec<PathBuf>> {
        let handles: Vec<_> = image_file_ids
            .iter()
            .map(|file_id| {
                let telegram_client = self.telegram_client.clone();
                let file_id = file_id.clone();
                tokio::spawn(async move { telegram_client.download_file_to_temp(&file_id).await })
            })
            .collect();

        let mut file_paths = Vec::with_capacity(handles.len());
        let mut failure: Option<anyhow::Error> = None;
        for handle in handles {
            match handle.await {
                Ok(Ok(file_path)) => file_paths.push(file_path),
                Ok(Err(error)) => failure = failure.or(Some(error)),
                Err(error) => failure = failure.or(Some(anyhow!(error))),
            }
        }
        match failure {
            // Everything already downloaded is cleaned up so nothing leaks.
            Some(error) => {
                self.cleanup(&file_paths).await;
                Err(error)
            }
            None => Ok(file_paths),
        }
    }

    /// Each download lives in its own temp directory, so cleanup removes the
    /// parent directories rather than the files.
    pub async fn cleanup(&self, file_paths: &[PathBuf]) {
        let directories: HashSet<&Path> = file_paths.iter().filter_map(|file_path| file_path.parent()).collect();
        for directory in directories {
            if let Err(error) = tokio::fs::remove_dir_all(directory).await {
                warn!("Failed to delete directory: {} ({error})", directory.display());
            }
        }
    }
}
