class UserMemoryRepository
  def list(telegram_user_id)
    UserMemory.where(telegram_user_id: telegram_user_id).order(:kind, :key).to_a
  end

  def touch_all(telegram_user_id, ids)
    return if ids.empty?

    now = current_time_ms
    UserMemory.where(telegram_user_id: telegram_user_id, id: ids).update_all(last_used_at: now)
  end

  def upsert_all(telegram_user_id, memories)
    return if memories.empty?

    now = current_time_ms
    rows = memories.map do |memory|
      {
        telegram_user_id: telegram_user_id,
        kind: memory.fetch(:kind),
        key: memory.fetch(:key),
        value: memory.fetch(:value),
        created_at: now,
        updated_at: now,
        last_used_at: now
      }
    end

    UserMemory.upsert_all(rows, unique_by: :index_user_memories_on_telegram_user_id_and_kind_and_key)
  end

  def clear(telegram_user_id)
    UserMemory.where(telegram_user_id: telegram_user_id).delete_all
  end

  private

  def current_time_ms
    (Time.now.to_f * 1000).to_i
  end
end
