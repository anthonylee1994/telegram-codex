class TelegramUpdateParser
  def parse_incoming_telegram_message(update)
    return build_callback_query_message(update) if supported_callback_query?(update)
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
    photo = message["photo"].is_a?(Array) && message["photo"].any?

    text || photo
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

  def build_message(update)
    message = update.fetch("message")

    build_inbound_message(
      update: update,
      callback_query_id: nil,
      chat_id: message.dig("chat", "id"),
      image_file_id: largest_photo_file_id(message),
      inline_callback: false,
      message_id: message["message_id"],
      text: message["text"].presence || message["caption"].to_s.strip,
      user_id: message.dig("from", "id")
    )
  end

  def build_callback_query_message(update)
    callback_query = update.fetch("callback_query")

    build_inbound_message(
      update: update,
      callback_query_id: callback_query["id"],
      chat_id: callback_query.dig("message", "chat", "id"),
      image_file_id: nil,
      inline_callback: true,
      message_id: callback_query.dig("message", "message_id"),
      text: callback_query["data"].to_s.strip,
      user_id: callback_query.dig("from", "id")
    )
  end

  def build_inbound_message(update:, callback_query_id:, chat_id:, image_file_id:, inline_callback:, message_id:, text:, user_id:)
    InboundTelegramMessage.new(
      callback_query_id: callback_query_id&.to_s,
      chat_id: chat_id.to_s,
      image_file_id: image_file_id,
      inline_callback: inline_callback,
      message_id: message_id,
      text: text,
      user_id: user_id.to_s,
      update_id: update.fetch("update_id")
    )
  end

  def largest_photo_file_id(message)
    largest_photo = Array(message["photo"]).max_by { |photo| photo["file_size"].to_i }
    largest_photo&.fetch("file_id", nil)
  end
end
