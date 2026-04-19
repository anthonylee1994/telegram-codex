class Conversation::Webhooks::ActionExecutor
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
      duplicate: Conversation::Webhooks::Action::Duplicate.new(**shared_dependencies),
      generate_reply: Conversation::Webhooks::Action::GenerateReply.new(**shared_dependencies),
      rate_limited: Conversation::Webhooks::Action::RateLimited.new(**shared_dependencies),
      reject_unauthorized: Conversation::Webhooks::Action::RejectUnauthorized.new(**shared_dependencies),
      replay: Conversation::Webhooks::Action::Replay.new(**shared_dependencies),
      reset_session: Conversation::Webhooks::Action::ResetSession.new(**shared_dependencies),
      show_help: Conversation::Webhooks::Action::ShowHelp.new(**shared_dependencies),
      show_session: Conversation::Webhooks::Action::ShowSession.new(**shared_dependencies),
      show_status: Conversation::Webhooks::Action::ShowStatus.new(**shared_dependencies),
      summarize_session: Conversation::Webhooks::Action::SummarizeSession.new(**summary_dependencies),
      too_many_images: Conversation::Webhooks::Action::TooManyImages.new(**shared_dependencies),
      unsupported: Conversation::Webhooks::Action::Unsupported.new(**shared_dependencies)
    }
  end

  def call(decision, update: nil)
    @actions.fetch(decision.action).call(decision, update: update)
  end
end
