class TelegramUpdateParser
  def parse_incoming_telegram_message(update)
    return nil unless supported_message?(update)

    build_message(update)
  end

  private

  def supported_message?(update)
    return false unless update.is_a?(Hash)
    return false unless update["update_id"].is_a?(Integer)

    message = update["message"]
    return false unless message.is_a?(Hash)
    return false unless message["from"].is_a?(Hash)
    return false unless message["chat"].is_a?(Hash)
    return false unless message["message_id"].is_a?(Integer)

    text = message["text"].is_a?(String)
    document_image = supported_document_image?(message["document"])
    pdf_document = supported_pdf_document?(message["document"])
    text_document = supported_text_document?(message["document"])
    photo = message["photo"].is_a?(Array) && message["photo"].any?

    text || photo || document_image || pdf_document || text_document
  end

  def build_message(update)
    message = update.fetch("message")

    build_inbound_message(
      update: update,
      chat_id: message.dig("chat", "id"),
      image_file_ids: build_image_file_ids(message),
      media_group_id: message["media_group_id"].to_s.presence,
      message_id: message["message_id"],
      pdf_file_id: build_pdf_file_id(message),
      text: message["text"].presence || message["caption"].to_s.strip,
      text_document_file_id: build_text_document_file_id(message),
      text_document_name: build_text_document_name(message),
      user_id: message.dig("from", "id")
    )
  end

  def build_inbound_message(update:, chat_id:, image_file_ids:, media_group_id:, message_id:, pdf_file_id:, text:,
                            text_document_file_id:, text_document_name:, user_id:)
    InboundTelegramMessage.new(
      chat_id: chat_id.to_s,
      image_file_ids: image_file_ids,
      media_group_id: media_group_id,
      message_id: message_id,
      pdf_file_id: pdf_file_id,
      text: text,
      text_document_file_id: text_document_file_id,
      text_document_name: text_document_name,
      user_id: user_id.to_s,
      update_id: update.fetch("update_id")
    )
  end

  def build_image_file_ids(message)
    return [message.fetch("document").fetch("file_id")] if supported_document_image?(message["document"])

    largest_photo = Array(message["photo"]).max_by { |photo| photo["file_size"].to_i }
    largest_photo&.then { |photo| [photo.fetch("file_id", nil)] }.to_a
  end

  def build_pdf_file_id(message)
    return nil unless supported_pdf_document?(message["document"])

    message.fetch("document").fetch("file_id")
  end

  def build_text_document_file_id(message)
    return nil unless supported_text_document?(message["document"])

    message.fetch("document").fetch("file_id")
  end

  def build_text_document_name(message)
    return nil unless supported_text_document?(message["document"])

    message.fetch("document")["file_name"].to_s.strip
  end

  def supported_document_image?(document)
    return false unless document.is_a?(Hash)
    return false unless document["file_id"].is_a?(String)

    mime_type = document["mime_type"].to_s.strip.downcase
    return true if mime_type.start_with?("image/")

    file_name = document["file_name"].to_s.strip.downcase
    %w[.jpg .jpeg .png .webp].any? { |extension| file_name.end_with?(extension) }
  end

  def supported_pdf_document?(document)
    return false unless document.is_a?(Hash)
    return false unless document["file_id"].is_a?(String)

    mime_type = document["mime_type"].to_s.strip.downcase
    return true if mime_type == "application/pdf"

    document["file_name"].to_s.strip.downcase.end_with?(".pdf")
  end

  def supported_text_document?(document)
    return false unless document.is_a?(Hash)
    return false unless document["file_id"].is_a?(String)
    return false if supported_document_image?(document) || supported_pdf_document?(document)

    mime_type = document["mime_type"].to_s.strip.downcase
    return true if %w[text/plain text/markdown text/html application/xhtml+xml application/json text/csv application/csv application/vnd.openxmlformats-officedocument.wordprocessingml.document application/vnd.openxmlformats-officedocument.spreadsheetml.sheet].include?(mime_type)

    file_name = document["file_name"].to_s.strip.downcase
    %w[.txt .md .html .json .csv .docx .xlsx].any? { |extension| file_name.end_with?(extension) }
  end
end
