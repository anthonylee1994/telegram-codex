class Conversation::ProcessedUpdateFlow
  def initialize(conversation_service:)
    @conversation_service = conversation_service
  end

  def find(update_id)
    @conversation_service.get_processed_update(update_id)
  end

  def begin_processing(message)
    claimed_update_ids = []

    message.processing_updates.each do |processing_update|
      claimed = @conversation_service.begin_processing(
        processing_update.fetch(:update_id),
        message.chat_id,
        processing_update.fetch(:message_id)
      )
      return rollback_processing_claims(claimed_update_ids) unless claimed

      claimed_update_ids << processing_update.fetch(:update_id)
    end

    true
  end

  def clear_processing(message)
    message.processing_updates.each do |processing_update|
      @conversation_service.clear_processing(processing_update.fetch(:update_id))
    end
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
    message.processing_updates.each do |processing_update|
      @conversation_service.mark_processed(
        processing_update.fetch(:update_id),
        message.chat_id,
        processing_update.fetch(:message_id)
      )
    end
  end

  private

  def rollback_processing_claims(claimed_update_ids)
    claimed_update_ids.each do |update_id|
      @conversation_service.clear_processing(update_id)
    end

    false
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
