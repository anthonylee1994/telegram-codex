class Conversation::ChatSessionRepository
  def initialize(config: AppConfig.fetch)
    @config = config
  end

  def find_active(chat_id)
    session = ChatSession.find_by(chat_id: chat_id)
    return nil if session.nil?

    return session if current_time_ms - session.updated_at <= session_ttl_ms

    ChatSession.where(chat_id: chat_id).delete_all
    Rails.logger.info("Reset expired session chat_id=#{chat_id}")
    nil
  end

  def persist(chat_id, conversation_state)
    record = ChatSession.find_or_initialize_by(chat_id: chat_id)
    record.last_response_id = conversation_state
    record.updated_at = current_time_ms
    record.save!
  end

  def reset(chat_id)
    ChatSession.where(chat_id: chat_id).delete_all
    Rails.logger.info("Reset chat session chat_id=#{chat_id}")
  end

  private

  def session_ttl_ms
    @config.session_ttl_days * 24 * 60 * 60 * 1000
  end

  def current_time_ms
    (Time.now.to_f * 1000).to_i
  end
end
