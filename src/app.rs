use std::sync::Arc;

use anyhow::Result;
use axum::Router;
use axum::routing::{get, post};

use crate::codex::execution::exec_runner::ExecRunner;
use crate::codex::memory::codex_memory_client::CodexMemoryClient;
use crate::codex::parsing::json_payload_parser::JsonPayloadParser;
use crate::codex::parsing::reply_parser::ReplyParser;
use crate::codex::reply::codex_reply_client::CodexReplyClient;
use crate::codex::reply::prompt_builder::PromptBuilder;
use crate::codex::session::codex_session_compact_client::CodexSessionCompactClient;
use crate::config::AppConfig;
use crate::conversation::reply::attachment_downloader::AttachmentDownloader;
use crate::conversation::reply::chat_rate_limiter::ChatRateLimiter;
use crate::conversation::reply::processed_update_service::ProcessedUpdateService;
use crate::conversation::reply::reply_generation::ReplyGenerationService;
use crate::conversation::scheduler::job_scheduler::{InboundMessageProcessorPort, JobScheduler};
use crate::conversation::session::session_service::SessionService;
use crate::conversation::storage::chat_memory_repository::ChatMemoryRepository;
use crate::conversation::storage::chat_session_repository::ChatSessionRepository;
use crate::conversation::storage::media_group_buffer_repository::MediaGroupBufferRepository;
use crate::conversation::storage::media_group_merger::MediaGroupMerger;
use crate::conversation::storage::processed_update_repository::ProcessedUpdateRepository;
use crate::database::{self, Database};
use crate::health::health_controller;
use crate::telegram::api::telegram_api::TelegramApi;
use crate::telegram::commands::compact_command_executor::CompactCommandExecutor;
use crate::telegram::commands::compact_result_sender::CompactResultSender;
use crate::telegram::commands::telegram_command_handler::TelegramCommandHandler;
use crate::telegram::commands::telegram_command_registry::TelegramCommandRegistry;
use crate::telegram::commands::telegram_command_responder::TelegramCommandResponder;
use crate::telegram::commands::telegram_status_message_builder::TelegramStatusMessageBuilder;
use crate::telegram::inbound::duplicate_update_handler::DuplicateUpdateHandler;
use crate::telegram::inbound::inbound_message_processor::InboundMessageProcessor;
use crate::telegram::inbound::reply_request_guard::ReplyRequestGuard;
use crate::telegram::inbound::unsupported_message_handler::UnsupportedMessageHandler;
use crate::telegram::shared::telegram_message_formatter::TelegramMessageFormatter;
use crate::telegram::shared::telegram_types::TelegramGateway;
use crate::telegram::shared::telegram_update_parser::TelegramUpdateParser;
use crate::telegram::webhook::telegram_webhook_controller::{self, SharedWebhookHandler};
use crate::telegram::webhook::telegram_webhook_service::{TelegramWebhookRouter, TelegramWebhookService};

#[derive(Clone)]
pub struct AppState {
    pub config: Arc<AppConfig>,
    pub webhook_handler: SharedWebhookHandler,
}

/// Everything the running process owns. Replaces the NestJS module graph with
/// explicit construction order.
pub struct App {
    pub state: AppState,
    pub database: Database,
    pub job_scheduler: Arc<JobScheduler>,
    _inbound_message_processor: Arc<InboundMessageProcessor>,
}

/// Builds only what the CLI tasks need, so they do not touch the database.
pub fn build_telegram_gateway(config: Arc<AppConfig>) -> Arc<dyn TelegramGateway> {
    let json_payload_parser = Arc::new(JsonPayloadParser::new());
    let reply_parser = Arc::new(ReplyParser::new(json_payload_parser));
    let formatter = Arc::new(TelegramMessageFormatter::new(Some(reply_parser)));
    Arc::new(TelegramApi::new(config, formatter))
}

pub async fn build(config: Arc<AppConfig>) -> Result<App> {
    let database = database::connect(&config.sqlite_db_path).await?;

    // Codex boundary.
    let json_payload_parser = Arc::new(JsonPayloadParser::new());
    let reply_parser = Arc::new(ReplyParser::new(json_payload_parser.clone()));
    let exec_runner = Arc::new(ExecRunner::new(config.clone()));
    let prompt_builder = Arc::new(PromptBuilder::new());
    let codex_reply_client = Arc::new(CodexReplyClient::new(exec_runner.clone(), prompt_builder, reply_parser.clone()));
    let codex_session_compact_client = Arc::new(CodexSessionCompactClient::new(exec_runner.clone()));
    let codex_memory_client = Arc::new(CodexMemoryClient::new(exec_runner, json_payload_parser));

    // Telegram boundary.
    let formatter = Arc::new(TelegramMessageFormatter::new(Some(reply_parser)));
    let telegram_update_parser = Arc::new(TelegramUpdateParser::new());
    let telegram_client: Arc<dyn TelegramGateway> = Arc::new(TelegramApi::new(config.clone(), formatter));

    // Persistence.
    let chat_session_repository = Arc::new(ChatSessionRepository::new(config.clone(), database.clone()));
    let chat_memory_repository = Arc::new(ChatMemoryRepository::new(database.clone()));
    let processed_update_repository = Arc::new(ProcessedUpdateRepository::new(database.clone()));
    let media_group_merger = Arc::new(MediaGroupMerger::new());
    let media_group_store = Arc::new(MediaGroupBufferRepository::new(database.clone(), media_group_merger));

    // Conversation use cases.
    let session_service = Arc::new(SessionService::new(chat_session_repository.clone(), codex_session_compact_client, chat_memory_repository.clone()));
    let processed_update_service = Arc::new(ProcessedUpdateService::new(processed_update_repository, session_service.clone()));
    let attachment_downloader = Arc::new(AttachmentDownloader::new(telegram_client.clone()));
    let rate_limiter = Arc::new(ChatRateLimiter::new(config.clone()));
    let reply_generation_service = Arc::new(ReplyGenerationService::new(
        codex_reply_client,
        chat_session_repository,
        chat_memory_repository,
        codex_memory_client,
        processed_update_service.clone(),
        session_service.clone(),
        telegram_client.clone(),
        attachment_downloader,
    ));

    // Scheduler is created before the inbound processor and learns about it
    // afterwards, which is the Rust counterpart of Nest's `forwardRef`.
    let compact_result_sender = Arc::new(CompactResultSender::new(telegram_client.clone()));
    let job_scheduler = Arc::new(JobScheduler::new(
        media_group_store.clone(),
        reply_generation_service,
        session_service.clone(),
        compact_result_sender.clone(),
    ));

    // Commands.
    let command_responder = Arc::new(TelegramCommandResponder::new(processed_update_service.clone(), telegram_client.clone(), compact_result_sender));
    let compact_command_executor = Arc::new(CompactCommandExecutor::new(session_service.clone(), job_scheduler.clone(), command_responder.clone()));
    let telegram_command_handler = Arc::new(TelegramCommandHandler::new(
        Arc::new(TelegramCommandRegistry::new()),
        session_service.clone(),
        Arc::new(TelegramStatusMessageBuilder::new(session_service)),
        command_responder,
        compact_command_executor,
    ));

    // Inbound pipeline.
    let inbound_message_processor = Arc::new(InboundMessageProcessor::new(
        Arc::new(UnsupportedMessageHandler::new(telegram_client.clone())),
        Arc::new(DuplicateUpdateHandler::new(processed_update_service.clone(), telegram_client.clone())),
        telegram_command_handler,
        Arc::new(ReplyRequestGuard::new(config.clone(), rate_limiter, processed_update_service, telegram_client)),
        media_group_store,
        job_scheduler.clone(),
        config.clone(),
    ));
    let processor_port: Arc<dyn InboundMessageProcessorPort> = inbound_message_processor.clone();
    job_scheduler.set_inbound_message_processor(Arc::downgrade(&processor_port));

    let webhook_handler: SharedWebhookHandler = Arc::new(TelegramWebhookService::new(
        telegram_update_parser,
        Arc::new(TelegramWebhookRouter::new(inbound_message_processor.clone())),
    ));

    Ok(App {
        state: AppState { config, webhook_handler },
        database,
        job_scheduler,
        _inbound_message_processor: inbound_message_processor,
    })
}

pub fn router(state: AppState) -> Router {
    Router::new()
        .route("/health", get(health_controller::show))
        .route("/telegram/webhook", post(telegram_webhook_controller::create))
        .with_state(state)
}
