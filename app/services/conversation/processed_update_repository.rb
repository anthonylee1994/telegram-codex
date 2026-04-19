class Conversation::ProcessedUpdateRepository
  INFLIGHT_TIMEOUT_MS = 5 * 60 * 1000

  def find(update_id)
    ProcessedUpdate.find_by(update_id: update_id)
  end

  def begin_processing(update_id, chat_id, message_id)
    now = current_time_ms

    ProcessedUpdate.transaction do
      processed_update = ProcessedUpdate.lock.find_by(update_id: update_id)

      return create_processing_claim(update_id, chat_id, message_id, now) if processed_update.nil?
      return false if processed_update.sent_at.present?
      return false if processed_update.reply_text.present? && processed_update.conversation_state.present?
      return false if inflight?(processed_update, now)

      processed_update.update!(
        chat_id: chat_id,
        message_id: message_id,
        processed_at: now,
        reply_text: nil,
        conversation_state: nil,
        suggested_replies: nil,
        sent_at: nil
      )
    end

    true
  rescue ActiveRecord::RecordNotUnique
    false
  end

  def clear_processing(update_id)
    ProcessedUpdate.where(update_id: update_id, sent_at: nil, reply_text: nil, conversation_state: nil).delete_all
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

  def create_processing_claim(update_id, chat_id, message_id, now)
    ProcessedUpdate.create!(
      update_id: update_id,
      chat_id: chat_id,
      message_id: message_id,
      processed_at: now
    )

    true
  end

  def inflight?(processed_update, now)
    now - processed_update.processed_at < INFLIGHT_TIMEOUT_MS
  end

  def upsert(attributes)
    ProcessedUpdate.upsert(attributes, unique_by: :index_processed_updates_on_update_id)
  end
end
