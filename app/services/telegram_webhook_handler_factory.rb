class TelegramWebhookHandlerFactory
  def self.build
    config = AppConfig.fetch
    conversation_service = ConversationService.new
    telegram_client = TelegramClient.new
    rate_limiter = ChatRateLimiter.instance
    telegram_update_parser = TelegramUpdateParser.new
    media_group_aggregator = MediaGroupAggregator.new(wait_duration_seconds: config.media_group_wait_ms / 1000.0)
    processed_update_flow = ProcessedUpdateFlow.new(conversation_service: conversation_service)
    decision_resolver = WebhookDecision::Resolver.new(
      processed_update_flow: processed_update_flow,
      rate_limiter: rate_limiter,
      config: config,
      start_message: TelegramWebhookHandler::START_MESSAGE,
      new_session_message: TelegramWebhookHandler::NEW_SESSION_MESSAGE,
      too_many_images_message: TelegramWebhookHandler::TOO_MANY_IMAGES_MESSAGE
    )
    action_executor = WebhookActionExecutor.new(
      conversation_service: conversation_service,
      telegram_client: telegram_client,
      processed_update_flow: processed_update_flow,
      reply_generation_job_class: ReplyGenerationJob,
      generic_error_message: TelegramWebhookHandler::GENERIC_ERROR_MESSAGE,
      unauthorized_message: TelegramWebhookHandler::UNAUTHORIZED_MESSAGE,
      rate_limit_message: TelegramWebhookHandler::RATE_LIMIT_MESSAGE,
      unsupported_message: TelegramWebhookHandler::UNSUPPORTED_MESSAGE
    )

    TelegramWebhookHandler.new(
      telegram_update_parser: telegram_update_parser,
      media_group_aggregator: media_group_aggregator,
      decision_resolver: decision_resolver,
      action_executor: action_executor
    )
  end
end
