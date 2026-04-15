class ProcessedUpdateRepository
  def find(update_id)
    ProcessedUpdate.find_by(update_id: update_id)
  end

  def mark_processed(update_id, chat_id, message_id)
    now = current_time_ms
    upsert(
      update_id: update_id,
      chat_id: chat_id,
      message_id: message_id,
      processed_at: now,
      sent_at: now
    )
  end

  def save_pending_reply(update_id, chat_id, message_id, result)
    now = current_time_ms
    upsert(
      update_id: update_id,
      chat_id: chat_id,
      message_id: message_id,
      processed_at: now,
      reply_text: result.fetch(:text),
      conversation_state: result.fetch(:conversation_state),
      suggested_replies: JSON.generate(Array(result[:suggested_replies])),
      sent_at: nil
    )
  end

  def prune_sent_before(cutoff)
    ProcessedUpdate.where.not(sent_at: nil).where("processed_at < ?", cutoff).delete_all
  end

  private

  def current_time_ms
    (Time.now.to_f * 1000).to_i
  end

  def upsert(attributes)
    ProcessedUpdate.upsert(attributes, unique_by: :index_processed_updates_on_update_id)
  end
end
