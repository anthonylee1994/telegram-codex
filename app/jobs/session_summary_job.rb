class SessionSummaryJob < ApplicationJob
  TIMEOUT_ERROR_MESSAGE = "我整理 session 摘要時卡住咗太耐。你可以等陣再試 `/summary`。"

  retry_on StandardError, wait: 1.second, attempts: 3 do |job, error|
    job.handle_retry_exhausted(error)
  end

  def perform(chat_id)
    result = conversation_service.summarize_session(chat_id)
    telegram_client.send_message(chat_id, build_message(result), remove_keyboard: true)
  end

  def handle_retry_exhausted(error)
    telegram_client.send_message(arguments.first, error_message_for(error), remove_keyboard: true)
  rescue StandardError => exhausted_error
    Rails.logger.error("Failed to send session summary fallback chat_id=#{arguments.first} error=#{exhausted_error.message}")
  end

  private

  def conversation_service
    @conversation_service ||= Conversation::Service.new
  end

  def telegram_client
    @telegram_client ||= Telegram::Client.new
  end

  def build_message(result)
    case result.fetch(:status)
    when :missing_session
      "而家冇 active session，冇嘢可以摘要。"
    when :too_short
      "目前對話得 #{result.fetch(:message_count)} 段訊息，未去到要壓縮 context。"
    when :ok
      [
        "已經將目前 session 壓縮成新 context。",
        "原本訊息：#{result.fetch(:original_message_count)}",
        "",
        result.fetch(:summary_text)
      ].join("\n")
    else
      Telegram::WebhookHandler::GENERIC_ERROR_MESSAGE
    end
  end

  def error_message_for(error)
    return TIMEOUT_ERROR_MESSAGE if error.is_a?(Codex::ExecRunner::ExecutionTimeoutError)

    Telegram::WebhookHandler::GENERIC_ERROR_MESSAGE
  end
end
