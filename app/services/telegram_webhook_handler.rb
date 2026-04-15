require "fileutils"

class TelegramWebhookHandler
  GENERIC_ERROR_MESSAGE = "我要休息一陣，遲啲叫醒我。"
  NEW_SESSION_MESSAGE = "已經開咗個新 session，你可以重新開始。"
  RATE_LIMIT_MESSAGE = "你打得太快，等一陣再試。"
  START_MESSAGE = [
    "您好，我係您嘅 AI 助手。",
    "",
    "直接 send 文字或者圖片畀我就得。",
    "想重新開過個 session，就打 `/new`。"
  ].join("\n").freeze
  UNAUTHORIZED_MESSAGE = "呢個 bot 暫時只限指定用戶使用。"
  UNSUPPORTED_MESSAGE = "而家只支援文字同圖片訊息，仲未支援檔案、語音。"

  def initialize(
    conversation_service: ConversationService.new,
    telegram_client: TelegramClient.new,
    rate_limiter: ChatRateLimiter.instance,
    telegram_update_parser: TelegramUpdateParser.new,
    config: AppConfig.fetch
  )
    @conversation_service = conversation_service
    @telegram_client = telegram_client
    @rate_limiter = rate_limiter
    @telegram_update_parser = telegram_update_parser
    @config = config
  end

  def handle(update)
    message = @telegram_update_parser.parse_incoming_telegram_message(update)
    return reply_unsupported(update) if unsupported_message?(message)

    processed_update = @conversation_service.get_processed_update(message.fetch(:update_id))
    return handle_duplicate_update(message) if processed_update_sent?(processed_update)
    return resend_pending_reply(message, processed_update) if replayable_processed_update?(processed_update)
    return reject_unauthorized_user(message) if unauthorized_user?(message.fetch(:user_id))
    return reset_session_with_message(message, NEW_SESSION_MESSAGE) if new_session_command?(message.fetch(:text))
    return reset_session_with_message(message, START_MESSAGE) if start_command?(message.fetch(:text))
    return handle_rate_limited(message) unless @rate_limiter.allow(message.fetch(:chat_id))

    generate_and_send_reply(message)
  end

  private

  def unsupported_message?(message)
    message.nil? || (message[:text].blank? && message[:image_file_id].blank?)
  end

  def reply_unsupported(update)
    return unless update.is_a?(Hash)

    chat_id = update.dig("message", "chat", "id")
    return if chat_id.nil?

    @telegram_client.send_message(chat_id.to_s, UNSUPPORTED_MESSAGE)
  end

  def unauthorized_user?(user_id)
    @config.allowed_telegram_user_ids.any? && !@config.allowed_telegram_user_ids.include?(user_id)
  end

  def processed_update_sent?(processed_update)
    processed_update&.sent_at.present?
  end

  def replayable_processed_update?(processed_update)
    processed_update&.reply_text.present? && processed_update.conversation_state.present?
  end

  def new_session_command?(text)
    text.match?(%r{\A/new(?:@[\w_]+)?\z}u)
  end

  def start_command?(text)
    text.match?(%r{\A/start(?:@[\w_]+)?\z}u)
  end

  def handle_duplicate_update(message)
    finalize_callback_message(message)
    Rails.logger.info("Ignored duplicate update update_id=#{message.fetch(:update_id)}")
  end

  def resend_pending_reply(message, processed_update)
    finalize_callback_message(message)
    @telegram_client.send_message(
      message.fetch(:chat_id),
      processed_update.reply_text,
      suggested_replies: parse_suggested_replies(processed_update.suggested_replies)
    )
    @conversation_service.persist_conversation_state(message.fetch(:chat_id), processed_update.conversation_state)
    mark_processed(message)
  end

  def reject_unauthorized_user(message)
    finalize_callback_message(message)
    Rails.logger.warn("Rejected unauthorized Telegram user chat_id=#{message.fetch(:chat_id)} user_id=#{message.fetch(:user_id)}")
    @telegram_client.send_message(message.fetch(:chat_id), UNAUTHORIZED_MESSAGE)
    mark_processed(message)
  end

  def reset_session_with_message(message, response_text)
    finalize_callback_message(message)
    @conversation_service.reset_session(message.fetch(:chat_id))
    @telegram_client.send_message(message.fetch(:chat_id), response_text, remove_keyboard: true)
    mark_processed(message)
  end

  def handle_rate_limited(message)
    finalize_callback_message(message)
    @telegram_client.send_message(message.fetch(:chat_id), RATE_LIMIT_MESSAGE)
    mark_processed(message)
  end

  def generate_and_send_reply(message)
    has_pending_reply = false

    begin
      finalize_callback_message(message)
      reply = @telegram_client.with_typing_status(message.fetch(:chat_id)) do
        image_file_path = download_image_if_needed(message)

        begin
          build_reply(message, image_file_path)
        ensure
          cleanup_downloaded_image(image_file_path)
        end
      end

      @conversation_service.save_pending_reply(message.fetch(:update_id), message.fetch(:chat_id), message.fetch(:message_id), reply)
      has_pending_reply = true
      @telegram_client.send_message(
        message.fetch(:chat_id),
        reply.fetch(:text),
        suggested_replies: reply.fetch(:suggested_replies)
      )
      @conversation_service.persist_conversation_state(message.fetch(:chat_id), reply.fetch(:conversation_state))
      mark_processed(message)
    rescue StandardError => e
      Rails.logger.error(
        "Failed to handle Telegram update update_id=#{message.fetch(:update_id)} chat_id=#{message.fetch(:chat_id)} error=#{e.message}"
      )
      raise if has_pending_reply

      @telegram_client.send_message(message.fetch(:chat_id), GENERIC_ERROR_MESSAGE)
    end
  end

  def build_reply(message, image_file_path)
    generated_reply = @conversation_service.generate_reply(message.merge(image_file_path: image_file_path))
    suggested_replies = @conversation_service.generate_suggested_replies(generated_reply.fetch(:conversation_state))

    generated_reply.merge(suggested_replies: suggested_replies)
  end

  def download_image_if_needed(message)
    return nil if message[:image_file_id].blank?

    @telegram_client.download_file_to_temp(message.fetch(:image_file_id))
  end

  def cleanup_downloaded_image(image_file_path)
    return if image_file_path.blank?

    FileUtils.rm_rf(File.dirname(image_file_path))
  end

  def finalize_callback_message(message)
    answer_callback_query(message)
    clear_callback_buttons(message)
  end

  def mark_processed(message)
    @conversation_service.mark_processed(message.fetch(:update_id), message.fetch(:chat_id), message.fetch(:message_id))
  end

  def answer_callback_query(message)
    return unless message[:inline_callback]

    @telegram_client.answer_callback_query(message.fetch(:callback_query_id))
  rescue StandardError => e
    Rails.logger.warn("Failed to answer callback query callback_query_id=#{message[:callback_query_id]} error=#{e.message}")
  end

  def clear_callback_buttons(message)
    return unless message[:inline_callback]

    @telegram_client.clear_message_reply_markup(message.fetch(:chat_id), message.fetch(:message_id))
  rescue StandardError => e
    Rails.logger.warn("Failed to clear callback buttons chat_id=#{message[:chat_id]} message_id=#{message[:message_id]} error=#{e.message}")
  end

  def parse_suggested_replies(raw_suggested_replies)
    return [] if raw_suggested_replies.blank?

    parsed_replies = JSON.parse(raw_suggested_replies)
    return [] unless parsed_replies.is_a?(Array)

    parsed_replies.filter_map do |reply|
      next unless reply.is_a?(String)

      normalized_reply = reply.strip
      next if normalized_reply.empty?

      normalized_reply
    end
  rescue JSON::ParserError
    []
  end
end
