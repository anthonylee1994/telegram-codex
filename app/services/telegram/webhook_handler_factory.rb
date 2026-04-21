class Telegram::WebhookHandlerFactory
  def self.build
    config = AppConfig.fetch
    conversation_service = Conversation::Service.new
    telegram_client = Telegram::Client.new
    rate_limiter = Conversation::ChatRateLimiter.instance
    telegram_update_parser = Telegram::UpdateParser.new
    media_group_aggregator = Telegram::MediaGroupAggregator.new(wait_duration_seconds: config.media_group_wait_ms / 1000.0)
    processed_update_flow = Conversation::ProcessedUpdateFlow.new(conversation_service: conversation_service)
    decision_resolver = Conversation::Webhooks::Decision::Resolver.new(
      processed_update_flow: processed_update_flow,
      rate_limiter: rate_limiter,
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
      reply_generation_job_class: ReplyGenerationJob,
      session_summary_job_class: SessionSummaryJob,
      generic_error_message: Telegram::WebhookHandler::GENERIC_ERROR_MESSAGE,
      unauthorized_message: Telegram::WebhookHandler::UNAUTHORIZED_MESSAGE,
      rate_limit_message: Telegram::WebhookHandler::RATE_LIMIT_MESSAGE,
      unsupported_message: Telegram::WebhookHandler::UNSUPPORTED_MESSAGE
    )

    Telegram::WebhookHandler.new(
      telegram_update_parser: telegram_update_parser,
      media_group_aggregator: media_group_aggregator,
      decision_resolver: decision_resolver,
      action_executor: action_executor
    )
  end
end
