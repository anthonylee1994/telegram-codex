module TelegramWebhookHandlerTestHelper
  def build_telegram_webhook_handler(
    reply_client:,
    telegram_client:,
    config:,
    reply_generation_job_class: ReplyGenerationJob,
    session_summary_job_class: SessionSummaryJob
  )
    Telegram::MediaGroupAggregator.reset!
    conversation_service = Conversation::Service.new(reply_client: reply_client)
    processed_update_flow = Conversation::ProcessedUpdateFlow.new(conversation_service: conversation_service)
    decision_resolver = Conversation::Webhooks::Decision::Resolver.new(
      processed_update_flow: processed_update_flow,
      rate_limiter: Conversation::ChatRateLimiter.instance,
      config: config,
      reset_memory_message: Telegram::WebhookHandler::RESET_MEMORY_MESSAGE,
      start_message: Telegram::WebhookHandler::START_MESSAGE,
      new_session_message: Telegram::WebhookHandler::NEW_SESSION_MESSAGE,
      too_many_images_message: Telegram::WebhookHandler::TOO_MANY_IMAGES_MESSAGE,
      summary_queued_message: Telegram::WebhookHandler::SUMMARY_QUEUED_MESSAGE
    )
    action_executor = Conversation::Webhooks::ActionExecutor.new(
      conversation_service: conversation_service,
      telegram_client: telegram_client,
      processed_update_flow: processed_update_flow,
      reply_generation_job_class: reply_generation_job_class,
      session_summary_job_class: session_summary_job_class,
      generic_error_message: Telegram::WebhookHandler::GENERIC_ERROR_MESSAGE,
      unauthorized_message: Telegram::WebhookHandler::UNAUTHORIZED_MESSAGE,
      rate_limit_message: Telegram::WebhookHandler::RATE_LIMIT_MESSAGE,
      unsupported_message: Telegram::WebhookHandler::UNSUPPORTED_MESSAGE
    )

    [
      Telegram::WebhookHandler.new(
        telegram_update_parser: Telegram::UpdateParser.new,
        media_group_aggregator: Telegram::MediaGroupAggregator.new(wait_duration_seconds: 0.05),
        decision_resolver: decision_resolver,
        action_executor: action_executor
      ),
      conversation_service
    ]
  end

  def telegram_test_config
    AppConfig::Config.new(
      allowed_telegram_user_ids: [],
      base_url: 'https://example.com',
      codex_exec_timeout_seconds: 300,
      max_media_group_images: 6,
      max_pdf_pages: 4,
      media_group_wait_ms: 1200,
      port: 3000,
      rate_limit_max_messages: 5,
      rate_limit_window_ms: 10_000,
      session_ttl_days: 7,
      sqlite_db_path: Rails.root.join('data/test.db').to_s,
      telegram_bot_token: 'token',
      telegram_webhook_secret: 'secret'
    )
  end
end
