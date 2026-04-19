class WebhookActionExecutor
  def initialize(
    conversation_service:,
    telegram_client:,
    processed_update_flow:,
    reply_generation_job_class:,
    session_summary_job_class:,
    generic_error_message:,
    unauthorized_message:,
    rate_limit_message:,
    unsupported_message:
  )
    shared_dependencies = {
      conversation_service: conversation_service,
      telegram_client: telegram_client,
      processed_update_flow: processed_update_flow,
      reply_generation_job_class: reply_generation_job_class,
      generic_error_message: generic_error_message,
      unauthorized_message: unauthorized_message,
      rate_limit_message: rate_limit_message,
      unsupported_message: unsupported_message
    }

    summary_dependencies = shared_dependencies.merge(session_summary_job_class: session_summary_job_class)

    @actions = {
      duplicate: WebhookAction::Duplicate.new(**shared_dependencies),
      generate_reply: WebhookAction::GenerateReply.new(**shared_dependencies),
      rate_limited: WebhookAction::RateLimited.new(**shared_dependencies),
      reject_unauthorized: WebhookAction::RejectUnauthorized.new(**shared_dependencies),
      replay: WebhookAction::Replay.new(**shared_dependencies),
      reset_session: WebhookAction::ResetSession.new(**shared_dependencies),
      show_help: WebhookAction::ShowHelp.new(**shared_dependencies),
      show_session: WebhookAction::ShowSession.new(**shared_dependencies),
      show_status: WebhookAction::ShowStatus.new(**shared_dependencies),
      summarize_session: WebhookAction::SummarizeSession.new(**summary_dependencies),
      too_many_images: WebhookAction::TooManyImages.new(**shared_dependencies),
      unsupported: WebhookAction::Unsupported.new(**shared_dependencies)
    }
  end

  def call(decision, update: nil)
    @actions.fetch(decision.action).call(decision, update: update)
  end
end
