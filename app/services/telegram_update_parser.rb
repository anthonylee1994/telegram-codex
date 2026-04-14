class TelegramUpdateParser
  def parse_incoming_telegram_message(update)
    return parse_callback_query(update) if supported_callback_query?(update)
    return nil unless supported_message?(update)

    largest_photo = Array(update.dig("message", "photo")).max_by { |photo| photo["file_size"].to_i }

    {
      callback_query_id: nil,
      chat_id: update.dig("message", "chat", "id").to_s,
      image_file_id: largest_photo&.fetch("file_id", nil),
      inline_callback: false,
      message_id: update.dig("message", "message_id"),
      text: update.dig("message", "text").presence || update.dig("message", "caption").to_s.strip,
      user_id: update.dig("message", "from", "id").to_s,
      update_id: update.fetch("update_id")
    }
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
    photo = message["photo"].is_a?(Array) && message["photo"].any?

    text || photo
  end

  def parse_callback_query(update)
    {
      callback_query_id: update.dig("callback_query", "id").to_s,
      chat_id: update.dig("callback_query", "message", "chat", "id").to_s,
      image_file_id: nil,
      inline_callback: true,
      message_id: update.dig("callback_query", "message", "message_id"),
      text: update.dig("callback_query", "data").to_s.strip,
      user_id: update.dig("callback_query", "from", "id").to_s,
      update_id: update.fetch("update_id")
    }
  end

  def supported_callback_query?(update)
    return false unless update.is_a?(Hash)
    return false unless update["update_id"].is_a?(Integer)

    callback_query = update["callback_query"]
    return false unless callback_query.is_a?(Hash)
    return false unless callback_query["id"].is_a?(String)
    return false unless callback_query["from"].is_a?(Hash)
    return false unless callback_query["message"].is_a?(Hash)
    return false unless callback_query.dig("message", "chat").is_a?(Hash)
    return false unless callback_query.dig("message", "message_id").is_a?(Integer)

    callback_query["data"].is_a?(String) && callback_query["data"].strip != ""
  end
end
