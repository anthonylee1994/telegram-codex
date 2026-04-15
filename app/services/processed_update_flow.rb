class ProcessedUpdateFlow
  def initialize(conversation_service:)
    @conversation_service = conversation_service
  end

  def find(update_id)
    @conversation_service.get_processed_update(update_id)
  end

  def duplicate?(processed_update)
    processed_update&.sent_at.present?
  end

  def replayable?(processed_update)
    processed_update&.reply_text.present? && processed_update.conversation_state.present?
  end

  def resend_pending_reply(message, processed_update, telegram_client:)
    telegram_client.send_message(
      message.chat_id,
      processed_update.reply_text,
      suggested_replies: parse_suggested_replies(processed_update.suggested_replies)
    )
    @conversation_service.persist_conversation_state(message.chat_id, processed_update.conversation_state)
    mark_processed(message)
  end

  def mark_processed(message)
    @conversation_service.mark_processed(message.update_id, message.chat_id, message.message_id)
  end

  private

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
