class ReplyGenerationJob < ApplicationJob
  TIMEOUT_ERROR_MESSAGE = "我卡住咗太耐，今次覆唔切。你可以等陣再試。"

  retry_on StandardError, wait: 1.second, attempts: 3 do |job, error|
    job.handle_retry_exhausted(error)
  end

  def perform(message_payload)
    @message_payload = message_payload
    message = InboundTelegramMessage.from_job_payload(message_payload)
    processed_update_flow = build_processed_update_flow
    processed_update = processed_update_flow.find(message.update_id)

    return if processed_update_flow.duplicate?(processed_update)
    return processed_update_flow.resend_pending_reply(message, processed_update, telegram_client: telegram_client) if processed_update_flow.replayable?(processed_update)

    reply_generation_flow.call(message)
  end

  def handle_retry_exhausted(error)
    message = InboundTelegramMessage.from_job_payload(@message_payload)
    processed_update_flow = build_processed_update_flow
    processed_update = processed_update_flow.find(message.update_id)

    Rails.logger.error(
      "Failed to exhaust reply generation retries update_id=#{message.update_id} chat_id=#{message.chat_id} error=#{error.message}"
    )

    return if processed_update_flow.duplicate?(processed_update)

    if processed_update_flow.replayable?(processed_update)
      processed_update_flow.resend_pending_reply(message, processed_update, telegram_client: telegram_client)
      return
    end

    telegram_client.send_message(message.chat_id, error_message_for(error))
  rescue StandardError => exhausted_error
    Rails.logger.error(
      "Failed to send retry exhaustion fallback update_id=#{message.update_id} chat_id=#{message.chat_id} error=#{exhausted_error.message}"
    )
  end

  private

  def build_processed_update_flow
    ProcessedUpdateFlow.new(conversation_service: conversation_service)
  end

  def conversation_service
    @conversation_service ||= ConversationService.new
  end

  def reply_generation_flow
    @reply_generation_flow ||= ReplyGenerationFlow.new(
      conversation_service: conversation_service,
      telegram_client: telegram_client
    )
  end

  def telegram_client
    @telegram_client ||= TelegramClient.new
  end

  def error_message_for(error)
    return TIMEOUT_ERROR_MESSAGE if error.is_a?(CodexExecRunner::ExecutionTimeoutError)

    TelegramWebhookHandler::GENERIC_ERROR_MESSAGE
  end
end
