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

    if message.nil? || (message[:text].blank? && message[:image_file_id].blank?)
      reply_unsupported(update)
      return
    end

    processed_update = @conversation_service.get_processed_update(message.fetch(:update_id))

    if processed_update&.sent_at.present?
      Rails.logger.info("Ignored duplicate update update_id=#{message.fetch(:update_id)}")
      answer_callback_query(message)
      clear_callback_buttons(message)
      return
    end

    if processed_update&.reply_text.present? && processed_update.conversation_state.present?
      answer_callback_query(message)
      clear_callback_buttons(message)
      @telegram_client.send_message(
        message.fetch(:chat_id),
        processed_update.reply_text,
        suggested_replies: parse_suggested_replies(processed_update.suggested_replies)
      )
      @conversation_service.persist_conversation_state(message.fetch(:chat_id), processed_update.conversation_state)
      @conversation_service.mark_processed(message.fetch(:update_id), message.fetch(:chat_id), message.fetch(:message_id))
      return
    end

    if unauthorized_user?(message.fetch(:user_id))
      answer_callback_query(message)
      clear_callback_buttons(message)
      Rails.logger.warn("Rejected unauthorized Telegram user chat_id=#{message.fetch(:chat_id)} user_id=#{message.fetch(:user_id)}")
      @telegram_client.send_message(message.fetch(:chat_id), UNAUTHORIZED_MESSAGE)
      @conversation_service.mark_processed(message.fetch(:update_id), message.fetch(:chat_id), message.fetch(:message_id))
      return
    end

    if new_session_command?(message.fetch(:text))
      answer_callback_query(message)
      clear_callback_buttons(message)
      @conversation_service.reset_session(message.fetch(:chat_id))
      @telegram_client.send_message(message.fetch(:chat_id), NEW_SESSION_MESSAGE)
      @conversation_service.mark_processed(message.fetch(:update_id), message.fetch(:chat_id), message.fetch(:message_id))
      return
    end

    if start_command?(message.fetch(:text))
      answer_callback_query(message)
      clear_callback_buttons(message)
      @conversation_service.reset_session(message.fetch(:chat_id))
      @telegram_client.send_message(message.fetch(:chat_id), START_MESSAGE)
      @conversation_service.mark_processed(message.fetch(:update_id), message.fetch(:chat_id), message.fetch(:message_id))
      return
    end

    unless @rate_limiter.allow(message.fetch(:chat_id))
      answer_callback_query(message)
      clear_callback_buttons(message)
      @telegram_client.send_message(message.fetch(:chat_id), RATE_LIMIT_MESSAGE)
      @conversation_service.mark_processed(message.fetch(:update_id), message.fetch(:chat_id), message.fetch(:message_id))
      return
    end

    has_pending_reply = false

    begin
      answer_callback_query(message)
      clear_callback_buttons(message)
      reply = @telegram_client.with_typing_status(message.fetch(:chat_id)) do
        image_file_path = message[:image_file_id].present? ? @telegram_client.download_file_to_temp(message.fetch(:image_file_id)) : nil

        begin
          generated_reply = @conversation_service.generate_reply(message.merge(image_file_path: image_file_path))
          suggested_replies = @conversation_service.generate_suggested_replies(generated_reply.fetch(:conversation_state))

          generated_reply.merge(suggested_replies: suggested_replies)
        ensure
          FileUtils.rm_rf(File.dirname(image_file_path)) if image_file_path.present?
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
      @conversation_service.mark_processed(message.fetch(:update_id), message.fetch(:chat_id), message.fetch(:message_id))
    rescue StandardError => e
      Rails.logger.error(
        "Failed to handle Telegram update update_id=#{message.fetch(:update_id)} chat_id=#{message.fetch(:chat_id)} error=#{e.message}"
      )
      raise if has_pending_reply

      @telegram_client.send_message(message.fetch(:chat_id), GENERIC_ERROR_MESSAGE)
    end
  end

  private

  def reply_unsupported(update)
    return unless update.is_a?(Hash)

    chat_id = update.dig("message", "chat", "id")
    return if chat_id.nil?

    @telegram_client.send_message(chat_id.to_s, UNSUPPORTED_MESSAGE)
  end

  def unauthorized_user?(user_id)
    @config.allowed_telegram_user_ids.any? && !@config.allowed_telegram_user_ids.include?(user_id)
  end

  def new_session_command?(text)
    text.match?(%r{\A/new(?:@[\w_]+)?\z}u)
  end

  def start_command?(text)
    text.match?(%r{\A/start(?:@[\w_]+)?\z}u)
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
