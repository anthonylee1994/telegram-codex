class Conversation::ChatMemoryRepository
  def find(chat_id)
    ChatMemory.find_by(chat_id: chat_id)
  end

  def persist(chat_id, memory_text)
    normalized_memory_text = memory_text.to_s.strip
    return reset(chat_id) if normalized_memory_text.empty?

    record = ChatMemory.find_or_initialize_by(chat_id: chat_id)
    record.memory_text = normalized_memory_text
    record.updated_at = current_time_ms
    record.save!
  end

  def reset(chat_id)
    ChatMemory.where(chat_id: chat_id).delete_all
    Rails.logger.info("Reset chat memory chat_id=#{chat_id}")
  end

  private

  def current_time_ms
    (Time.now.to_f * 1000).to_i
  end
end
