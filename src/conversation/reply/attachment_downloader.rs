use std::collections::HashSet;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use anyhow::{Result, anyhow};
use tokio::sync::Semaphore;
use tracing::warn;

use crate::telegram::shared::telegram_types::TelegramGateway;

/// Caps how many transfers are in flight at once, process wide, so a ten image
/// album cannot multiply the per download buffers by ten.
const MAX_CONCURRENT_DOWNLOADS: usize = 3;

pub struct AttachmentDownloader {
    telegram_client: Arc<dyn TelegramGateway>,
    download_slots: Arc<Semaphore>,
}

impl AttachmentDownloader {
    pub fn new(telegram_client: Arc<dyn TelegramGateway>) -> Self {
        Self {
            telegram_client,
            download_slots: Arc::new(Semaphore::new(MAX_CONCURRENT_DOWNLOADS)),
        }
    }

    /// Downloads images concurrently while preserving album order, which the
    /// prompts rely on ("圖 1", "圖 2", …). Every task is spawned up front but
    /// only `MAX_CONCURRENT_DOWNLOADS` of them hold a slot at a time.
    pub async fn download_images(&self, image_file_ids: &[String]) -> Result<Vec<PathBuf>> {
        let handles: Vec<_> = image_file_ids
            .iter()
            .map(|file_id| {
                let telegram_client = self.telegram_client.clone();
                let download_slots = self.download_slots.clone();
                let file_id = file_id.clone();
                tokio::spawn(async move {
                    let _permit = download_slots.acquire().await?;
                    telegram_client.download_file_to_temp(&file_id).await
                })
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
