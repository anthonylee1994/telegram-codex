class InboundTelegramMessage
  attr_reader :chat_id, :image_file_ids, :media_group_id, :message_id, :pdf_file_id, :processing_updates,
              :reply_to_image_file_ids, :reply_to_message_id, :reply_to_pdf_file_id, :reply_to_text,
              :reply_to_text_document_file_id, :reply_to_text_document_name, :text, :text_document_file_id,
              :text_document_name, :user_id, :update_id

  def initialize(
    chat_id:,
    image_file_ids:,
    media_group_id: nil,
    message_id:,
    pdf_file_id: nil,
    processing_updates: nil,
    reply_to_image_file_ids: [],
    reply_to_message_id: nil,
    reply_to_pdf_file_id: nil,
    reply_to_text: nil,
    reply_to_text_document_file_id: nil,
    reply_to_text_document_name: nil,
    text:,
    text_document_file_id: nil,
    text_document_name: nil,
    user_id:,
    update_id:
  )
    @chat_id = chat_id
    @image_file_ids = normalize_image_file_ids(image_file_ids)
    @media_group_id = media_group_id
    @message_id = message_id
    @pdf_file_id = normalize_pdf_file_id(pdf_file_id)
    @processing_updates = normalize_processing_updates(processing_updates, update_id, message_id)
    @reply_to_image_file_ids = normalize_image_file_ids(reply_to_image_file_ids)
    @reply_to_message_id = normalize_reply_to_message_id(reply_to_message_id)
    @reply_to_pdf_file_id = normalize_pdf_file_id(reply_to_pdf_file_id)
    @reply_to_text = normalize_reply_to_text(reply_to_text)
    @reply_to_text_document_file_id = normalize_text_document_file_id(reply_to_text_document_file_id)
    @reply_to_text_document_name = normalize_text_document_name(reply_to_text_document_name)
    @text = text
    @text_document_file_id = normalize_text_document_file_id(text_document_file_id)
    @text_document_name = normalize_text_document_name(text_document_name)
    @user_id = user_id
    @update_id = update_id
  end

  def image_file_id
    image_file_ids.first
  end

  def image_count
    image_file_ids.length
  end

  def effective_image_file_ids
    return image_file_ids if image_file_ids.present?

    reply_to_image_file_ids
  end

  def effective_pdf_file_id
    return pdf_file_id if pdf_file_id.present?
    return nil if direct_attachment?

    reply_to_pdf_file_id
  end

  def effective_text_document_file_id
    return text_document_file_id if text_document_file_id.present?
    return nil if direct_attachment?

    reply_to_text_document_file_id
  end

  def effective_text_document_name
    return text_document_name if text_document_name.present?
    return nil if direct_attachment?

    reply_to_text_document_name
  end

  def media_group?
    media_group_id.present?
  end

  def pdf?
    pdf_file_id.present?
  end

  def text_document?
    text_document_file_id.present?
  end

  def unsupported?
    text.blank? && image_file_ids.empty? && pdf_file_id.blank? && text_document_file_id.blank?
  end

  def replied_message?
    reply_to_message_id.present?
  end

  def replying_to_file?
    reply_to_image_file_ids.present? || reply_to_pdf_file_id.present? || reply_to_text_document_file_id.present?
  end

  def to_job_payload
    {
      "chat_id" => chat_id,
      "image_file_ids" => image_file_ids,
      "media_group_id" => media_group_id,
      "message_id" => message_id,
      "pdf_file_id" => pdf_file_id,
      "processing_updates" => processing_updates.map do |processing_update|
        {
          "update_id" => processing_update.fetch(:update_id),
          "message_id" => processing_update.fetch(:message_id)
        }
      end,
      "reply_to_image_file_ids" => reply_to_image_file_ids,
      "reply_to_message_id" => reply_to_message_id,
      "reply_to_pdf_file_id" => reply_to_pdf_file_id,
      "reply_to_text" => reply_to_text,
      "reply_to_text_document_file_id" => reply_to_text_document_file_id,
      "reply_to_text_document_name" => reply_to_text_document_name,
      "text" => text,
      "text_document_file_id" => text_document_file_id,
      "text_document_name" => text_document_name,
      "user_id" => user_id,
      "update_id" => update_id
    }
  end

  def self.from_job_payload(payload)
    new(
      chat_id: payload.fetch("chat_id"),
      image_file_ids: payload.fetch("image_file_ids", []),
      media_group_id: payload["media_group_id"],
      message_id: payload.fetch("message_id"),
      pdf_file_id: payload["pdf_file_id"],
      processing_updates: payload.fetch("processing_updates", []),
      reply_to_image_file_ids: payload.fetch("reply_to_image_file_ids", []),
      reply_to_message_id: payload["reply_to_message_id"],
      reply_to_pdf_file_id: payload["reply_to_pdf_file_id"],
      reply_to_text: payload["reply_to_text"],
      reply_to_text_document_file_id: payload["reply_to_text_document_file_id"],
      reply_to_text_document_name: payload["reply_to_text_document_name"],
      text: payload["text"],
      text_document_file_id: payload["text_document_file_id"],
      text_document_name: payload["text_document_name"],
      user_id: payload.fetch("user_id"),
      update_id: payload.fetch("update_id")
    )
  end

  private

  def direct_attachment?
    image_file_ids.present? || pdf_file_id.present? || text_document_file_id.present?
  end

  def normalize_image_file_ids(image_file_ids)
    Array(image_file_ids).filter_map do |image_file_id|
      normalized_image_file_id = image_file_id.to_s.strip
      next if normalized_image_file_id.empty?

      normalized_image_file_id
    end.uniq
  end

  def normalize_pdf_file_id(pdf_file_id)
    normalized_pdf_file_id = pdf_file_id.to_s.strip
    return nil if normalized_pdf_file_id.empty?

    normalized_pdf_file_id
  end

  def normalize_reply_to_message_id(reply_to_message_id)
    Integer(reply_to_message_id)
  rescue ArgumentError, TypeError
    nil
  end

  def normalize_reply_to_text(reply_to_text)
    normalized_reply_to_text = reply_to_text.to_s.strip
    return nil if normalized_reply_to_text.empty?

    normalized_reply_to_text
  end

  def normalize_text_document_file_id(text_document_file_id)
    normalized_text_document_file_id = text_document_file_id.to_s.strip
    return nil if normalized_text_document_file_id.empty?

    normalized_text_document_file_id
  end

  def normalize_text_document_name(text_document_name)
    normalized_text_document_name = text_document_name.to_s.strip
    return nil if normalized_text_document_name.empty?

    normalized_text_document_name
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
