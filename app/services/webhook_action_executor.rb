class WebhookActionExecutor
  def initialize(
    conversation_service:,
    telegram_client:,
    processed_update_flow:,
    reply_generation_flow:,
    generic_error_message:,
    unauthorized_message:,
    rate_limit_message:,
    unsupported_message:
  )
    shared_dependencies = {
      conversation_service: conversation_service,
      telegram_client: telegram_client,
      processed_update_flow: processed_update_flow,
      reply_generation_flow: reply_generation_flow,
      generic_error_message: generic_error_message,
      unauthorized_message: unauthorized_message,
      rate_limit_message: rate_limit_message,
      unsupported_message: unsupported_message
    }

    @actions = {
      duplicate: WebhookAction::Duplicate.new(**shared_dependencies),
      generate_reply: WebhookAction::GenerateReply.new(**shared_dependencies),
      rate_limited: WebhookAction::RateLimited.new(**shared_dependencies),
      reject_unauthorized: WebhookAction::RejectUnauthorized.new(**shared_dependencies),
      replay: WebhookAction::Replay.new(**shared_dependencies),
      reset_session: WebhookAction::ResetSession.new(**shared_dependencies),
      unsupported: WebhookAction::Unsupported.new(**shared_dependencies)
    }
  end

  def call(decision, update: nil)
    @actions.fetch(decision.action).call(decision, update: update)
  end
end
