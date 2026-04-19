class WebhookAction
  def initialize(
    conversation_service:,
    telegram_client:,
    processed_update_flow:,
    reply_generation_job_class:,
    generic_error_message:,
    unauthorized_message:,
    rate_limit_message:,
    unsupported_message:
  )
    @conversation_service = conversation_service
    @telegram_client = telegram_client
    @processed_update_flow = processed_update_flow
    @reply_generation_job_class = reply_generation_job_class
    @generic_error_message = generic_error_message
    @unauthorized_message = unauthorized_message
    @rate_limit_message = rate_limit_message
    @unsupported_message = unsupported_message
  end

  private

  attr_reader :conversation_service, :telegram_client, :processed_update_flow, :reply_generation_job_class,
              :generic_error_message, :unauthorized_message, :rate_limit_message, :unsupported_message

  class Unsupported < WebhookAction
    def call(decision, update: nil)
      return unless update.is_a?(Hash)

      chat_id = update.dig("message", "chat", "id")
      return if chat_id.nil?

      telegram_client.send_message(chat_id.to_s, unsupported_message)
    end
  end

  class Duplicate < WebhookAction
    def call(decision, update: nil)
      Rails.logger.info("Ignored duplicate update update_id=#{decision.message.update_id}")
    end
  end

  class Replay < WebhookAction
    def call(decision, update: nil)
      processed_update_flow.resend_pending_reply(decision.message, decision.processed_update, telegram_client: telegram_client)
    end
  end

  class RejectUnauthorized < WebhookAction
    def call(decision, update: nil)
      message = decision.message
      Rails.logger.warn("Rejected unauthorized Telegram user chat_id=#{message.chat_id} user_id=#{message.user_id}")
      telegram_client.send_message(message.chat_id, unauthorized_message)
      processed_update_flow.mark_processed(message)
    end
  end

  class ResetSession < WebhookAction
    def call(decision, update: nil)
      message = decision.message
      conversation_service.reset_session(message.chat_id)
      telegram_client.send_message(message.chat_id, decision.response_text, remove_keyboard: true)
      processed_update_flow.mark_processed(message)
    end
  end

  class RateLimited < WebhookAction
    def call(decision, update: nil)
      message = decision.message
      telegram_client.send_message(message.chat_id, rate_limit_message)
      processed_update_flow.mark_processed(message)
    end
  end

  class TooManyImages < WebhookAction
    def call(decision, update: nil)
      message = decision.message
      telegram_client.send_message(message.chat_id, decision.response_text)
      processed_update_flow.mark_processed(message)
    end
  end

  class GenerateReply < WebhookAction
    def call(decision, update: nil)
      message = decision.message
      reply_generation_job_class.perform_later(message.to_job_payload)
    rescue StandardError => e
      processed_update_flow.clear_processing(message)
      Rails.logger.error(
        "Failed to enqueue Telegram update update_id=#{message.update_id} chat_id=#{message.chat_id} error=#{e.message}"
      )
      raise
    end
  end
end
