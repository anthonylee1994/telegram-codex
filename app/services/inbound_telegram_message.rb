class InboundTelegramMessage
  attr_reader :chat_id, :image_file_ids, :media_group_id, :message_id, :processing_updates, :text, :user_id, :update_id

  def initialize(
    chat_id:,
    image_file_ids:,
    media_group_id: nil,
    message_id:,
    processing_updates: nil,
    text:,
    user_id:,
    update_id:
  )
    @chat_id = chat_id
    @image_file_ids = normalize_image_file_ids(image_file_ids)
    @media_group_id = media_group_id
    @message_id = message_id
    @processing_updates = normalize_processing_updates(processing_updates, update_id, message_id)
    @text = text
    @user_id = user_id
    @update_id = update_id
  end

  def image_file_id
    image_file_ids.first
  end

  def media_group?
    media_group_id.present?
  end

  def unsupported?
    text.blank? && image_file_ids.empty?
  end

  private

  def normalize_image_file_ids(image_file_ids)
    Array(image_file_ids).filter_map do |image_file_id|
      normalized_image_file_id = image_file_id.to_s.strip
      next if normalized_image_file_id.empty?

      normalized_image_file_id
    end.uniq
  end

  def normalize_processing_updates(processing_updates, update_id, message_id)
    updates = Array(processing_updates)
    updates = [{ update_id: update_id, message_id: message_id }] if updates.empty?

    updates.filter_map do |processing_update|
      next unless processing_update.is_a?(Hash)

      normalized_update_id = Integer(processing_update[:update_id] || processing_update["update_id"])
      normalized_message_id = Integer(processing_update[:message_id] || processing_update["message_id"])
      { update_id: normalized_update_id, message_id: normalized_message_id }
    rescue ArgumentError, TypeError
      nil
    end.sort_by { |processing_update| [processing_update[:message_id], processing_update[:update_id]] }
  end
end
