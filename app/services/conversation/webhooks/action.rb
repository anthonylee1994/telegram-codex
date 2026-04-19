class Conversation::Webhooks::Action
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

  class Unsupported < Conversation::Webhooks::Action
    def call(decision, update: nil)
      return unless update.is_a?(Hash)

      chat_id = update.dig("message", "chat", "id")
      return if chat_id.nil?

      telegram_client.send_message(chat_id.to_s, unsupported_message)
    end
  end

  class Duplicate < Conversation::Webhooks::Action
    def call(decision, update: nil)
      Rails.logger.info("Ignored duplicate update update_id=#{decision.message.update_id}")
    end
  end

  class Replay < Conversation::Webhooks::Action
    def call(decision, update: nil)
      processed_update_flow.resend_pending_reply(decision.message, decision.processed_update, telegram_client: telegram_client)
    end
  end

  class RejectUnauthorized < Conversation::Webhooks::Action
    def call(decision, update: nil)
      message = decision.message
      Rails.logger.warn("Rejected unauthorized Telegram user chat_id=#{message.chat_id} user_id=#{message.user_id}")
      telegram_client.send_message(message.chat_id, unauthorized_message)
      processed_update_flow.mark_processed(message)
    end
  end

  class ResetSession < Conversation::Webhooks::Action
    def call(decision, update: nil)
      message = decision.message
      conversation_service.reset_session(message.chat_id)
      telegram_client.send_message(message.chat_id, decision.response_text, remove_keyboard: true)
      processed_update_flow.mark_processed(message)
    end
  end

  class ShowHelp < Conversation::Webhooks::Action
    def call(decision, update: nil)
      message = decision.message
      telegram_client.send_message(message.chat_id, Telegram::WebhookHandler::HELP_MESSAGE, remove_keyboard: true)
      processed_update_flow.mark_processed(message)
    end
  end

  class ShowStatus < Conversation::Webhooks::Action
    def call(decision, update: nil)
      message = decision.message
      telegram_client.send_message(message.chat_id, build_status_message(message.chat_id), remove_keyboard: true)
      processed_update_flow.mark_processed(message)
    end

    private

    def build_status_message(chat_id)
      snapshot = conversation_service.session_snapshot(chat_id)

      [
        "Bot 狀態：OK 🤖",
        "Session 狀態：#{snapshot[:active] ? "已生效 ✅" : "未生效 ❌"}",
        "支援：文字、圖片、多圖、圖片 document、PDF、txt/md/html/json/csv、docx/xlsx"
      ].join("\n")
    end

    def queue_adapter_label
      Rails.application.config.active_job.queue_adapter.class.name.demodulize.underscore
    end
  end

  class ShowSession < Conversation::Webhooks::Action
    def call(decision, update: nil)
      message = decision.message
      telegram_client.send_message(message.chat_id, build_session_message(message.chat_id), remove_keyboard: true)
      processed_update_flow.mark_processed(message)
    end

    private

    def build_session_message(chat_id)
      snapshot = conversation_service.session_snapshot(chat_id)
      return "目前冇 active session。你可以直接 send 訊息開始，或者之後用 `/summary` 壓縮長對話。" unless snapshot[:active]

      [
        "目前 session：active",
        "訊息數：#{snapshot.fetch(:message_count)}",
        "大概輪數：#{snapshot.fetch(:turn_count)}",
        "最後更新：#{snapshot.fetch(:last_updated_at).strftime('%Y-%m-%d %H:%M:%S %Z')}",
        "想壓縮 context 可以打 `/summary`。"
      ].join("\n")
    end
  end

  class SummarizeSession < Conversation::Webhooks::Action
    def initialize(session_summary_job_class:, **)
      super(**)
      @session_summary_job_class = session_summary_job_class
    end

    def call(decision, update: nil)
      message = decision.message
      @session_summary_job_class.perform_later(message.chat_id)
      telegram_client.send_message(message.chat_id, decision.response_text, remove_keyboard: true)
      processed_update_flow.mark_processed(message)
    rescue StandardError
      processed_update_flow.clear_processing(message)
      raise
    end
  end

  class RateLimited < Conversation::Webhooks::Action
    def call(decision, update: nil)
      message = decision.message
      telegram_client.send_message(message.chat_id, rate_limit_message)
      processed_update_flow.mark_processed(message)
    end
  end

  class TooManyImages < Conversation::Webhooks::Action
    def call(decision, update: nil)
      message = decision.message
      telegram_client.send_message(message.chat_id, decision.response_text)
      processed_update_flow.mark_processed(message)
    end
  end

  class GenerateReply < Conversation::Webhooks::Action
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
