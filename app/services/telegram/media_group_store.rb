class Telegram::MediaGroupStore
  def enqueue(message, wait_duration_seconds:)
    deadline_at = current_time_ms + wait_duration_ms(wait_duration_seconds)
    key = build_key(message)

    ApplicationRecord.transaction do
      MediaGroupBuffer.upsert(
        {
          key: key,
          deadline_at: deadline_at
        },
        unique_by: :index_media_group_buffers_on_key
      )

      MediaGroupMessage.upsert(
        {
          update_id: message.update_id,
          media_group_key: key,
          message_id: message.message_id,
          payload: JSON.generate(message.to_job_payload)
        },
        unique_by: :update_id
      )
    end

    { deadline_at: deadline_at, key: key }
  end

  def flush(key, expected_deadline_at:)
    ApplicationRecord.transaction do
      buffer = MediaGroupBuffer.lock.find_by(key: key)
      return { status: :missing } if buffer.nil?
      return { status: :stale } if buffer.deadline_at != expected_deadline_at

      wait_duration_ms = buffer.deadline_at - current_time_ms
      return { status: :pending, wait_duration_seconds: wait_duration_ms / 1000.0 } if wait_duration_ms.positive?

      rows = MediaGroupMessage.lock.where(media_group_key: key).order(:message_id, :update_id).to_a
      MediaGroupMessage.where(media_group_key: key).delete_all
      buffer.destroy!
      return { status: :missing } if rows.empty?

      { status: :ready, message: aggregate_messages(rows) }
    end
  end

  def clear!
    ApplicationRecord.transaction do
      MediaGroupMessage.delete_all
      MediaGroupBuffer.delete_all
    end
  end

  private

  def aggregate_messages(rows)
    messages = rows.map do |row|
      Telegram::InboundMessage.from_job_payload(JSON.parse(row.payload))
    end
    ordered_messages = messages.sort_by { |message| [message.message_id, message.update_id] }
    primary_message = ordered_messages.first
    aggregated_text = ordered_messages.filter_map { |message| message.text.presence }.first.to_s
    aggregated_image_file_ids = ordered_messages.flat_map(&:image_file_ids).uniq
    processing_updates = ordered_messages.map do |message|
      { update_id: message.update_id, message_id: message.message_id }
    end

    Telegram::InboundMessage.new(
      chat_id: primary_message.chat_id,
      image_file_ids: aggregated_image_file_ids,
      media_group_id: primary_message.media_group_id,
      message_id: primary_message.message_id,
      processing_updates: processing_updates,
      text: aggregated_text,
      user_id: primary_message.user_id,
      update_id: primary_message.update_id
    )
  end

  def build_key(message)
    "#{message.chat_id}:#{message.media_group_id}"
  end

  def current_time_ms
    (Time.now.to_f * 1000).to_i
  end

  def wait_duration_ms(wait_duration_seconds)
    (wait_duration_seconds * 1000).ceil
  end
end
