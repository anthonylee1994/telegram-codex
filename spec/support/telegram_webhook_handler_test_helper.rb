module TelegramWebhookHandlerTestHelper
  def build_telegram_webhook_handler(reply_client:, telegram_client:, config:, reply_generation_job_class: ReplyGenerationJob)
    MediaGroupAggregator.reset!
    conversation_service = ConversationService.new(reply_client: reply_client)
    processed_update_flow = ProcessedUpdateFlow.new(conversation_service: conversation_service)
    decision_resolver = WebhookDecision::Resolver.new(
      processed_update_flow: processed_update_flow,
      rate_limiter: ChatRateLimiter.instance,
      config: config,
      start_message: TelegramWebhookHandler::START_MESSAGE,
      new_session_message: TelegramWebhookHandler::NEW_SESSION_MESSAGE,
      too_many_images_message: TelegramWebhookHandler::TOO_MANY_IMAGES_MESSAGE
    )
    action_executor = WebhookActionExecutor.new(
      conversation_service: conversation_service,
      telegram_client: telegram_client,
      processed_update_flow: processed_update_flow,
      reply_generation_job_class: reply_generation_job_class,
      generic_error_message: TelegramWebhookHandler::GENERIC_ERROR_MESSAGE,
      unauthorized_message: TelegramWebhookHandler::UNAUTHORIZED_MESSAGE,
      rate_limit_message: TelegramWebhookHandler::RATE_LIMIT_MESSAGE,
      unsupported_message: TelegramWebhookHandler::UNSUPPORTED_MESSAGE
    )

    [
      TelegramWebhookHandler.new(
        telegram_update_parser: TelegramUpdateParser.new,
        media_group_aggregator: MediaGroupAggregator.new(wait_duration_seconds: 0.05),
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
