class WebhookAction
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
    @conversation_service = conversation_service
    @telegram_client = telegram_client
    @processed_update_flow = processed_update_flow
    @reply_generation_flow = reply_generation_flow
    @generic_error_message = generic_error_message
    @unauthorized_message = unauthorized_message
    @rate_limit_message = rate_limit_message
    @unsupported_message = unsupported_message
  end

  private

  attr_reader :conversation_service, :telegram_client, :processed_update_flow, :reply_generation_flow,
              :generic_error_message, :unauthorized_message, :rate_limit_message, :unsupported_message

  def finalize_callback_message(message)
    answer_callback_query(message)
    clear_callback_buttons(message)
  end

  def answer_callback_query(message)
    return unless message.inline_callback?

    telegram_client.answer_callback_query(message.callback_query_id)
  rescue StandardError => e
    Rails.logger.warn("Failed to answer callback query callback_query_id=#{message.callback_query_id} error=#{e.message}")
  end

  def clear_callback_buttons(message)
    return unless message.inline_callback?

    telegram_client.clear_message_reply_markup(message.chat_id, message.message_id)
  rescue StandardError => e
    Rails.logger.warn("Failed to clear callback buttons chat_id=#{message.chat_id} message_id=#{message.message_id} error=#{e.message}")
  end

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
      finalize_callback_message(decision.message)
      Rails.logger.info("Ignored duplicate update update_id=#{decision.message.update_id}")
    end
  end

  class Replay < WebhookAction
    def call(decision, update: nil)
      finalize_callback_message(decision.message)
      processed_update_flow.resend_pending_reply(decision.message, decision.processed_update, telegram_client: telegram_client)
    end
  end

  class RejectUnauthorized < WebhookAction
    def call(decision, update: nil)
      message = decision.message
      finalize_callback_message(message)
      Rails.logger.warn("Rejected unauthorized Telegram user chat_id=#{message.chat_id} user_id=#{message.user_id}")
      telegram_client.send_message(message.chat_id, unauthorized_message)
      processed_update_flow.mark_processed(message)
    end
  end

  class ResetSession < WebhookAction
    def call(decision, update: nil)
      message = decision.message
      finalize_callback_message(message)
      conversation_service.reset_session(message.chat_id)
      telegram_client.send_message(message.chat_id, decision.response_text, remove_keyboard: true)
      processed_update_flow.mark_processed(message)
    end
  end

  class RateLimited < WebhookAction
    def call(decision, update: nil)
      message = decision.message
      finalize_callback_message(message)
      telegram_client.send_message(message.chat_id, rate_limit_message)
      processed_update_flow.mark_processed(message)
    end
  end

  class ShowMemory < WebhookAction
    def call(decision, update: nil)
      message = decision.message
      finalize_callback_message(message)
      telegram_client.send_message(message.chat_id, conversation_service.format_memory(message.user_id), remove_keyboard: true)
      processed_update_flow.mark_processed(message)
    end
  end

  class ClearMemory < WebhookAction
    def call(decision, update: nil)
      message = decision.message
      finalize_callback_message(message)
      conversation_service.clear_memory(message.user_id)
      telegram_client.send_message(message.chat_id, TelegramWebhookHandler::MEMORY_CLEARED_MESSAGE, remove_keyboard: true)
      processed_update_flow.mark_processed(message)
    end
  end

  class GenerateReply < WebhookAction
    def call(decision, update: nil)
      message = decision.message

      begin
        finalize_callback_message(message)
        reply_generation_flow.call(message)
      rescue StandardError => e
        Rails.logger.error(
          "Failed to handle Telegram update update_id=#{message.update_id} chat_id=#{message.chat_id} error=#{e.message}"
        )
        processed_update = processed_update_flow.find(message.update_id)
        raise if processed_update_flow.replayable?(processed_update)

        telegram_client.send_message(message.chat_id, generic_error_message)
      end
    end
  end
end
