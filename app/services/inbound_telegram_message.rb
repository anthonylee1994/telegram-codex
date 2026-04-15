class InboundTelegramMessage
  attr_reader :callback_query_id, :chat_id, :image_file_id, :message_id, :text, :user_id, :update_id

  def initialize(callback_query_id:, chat_id:, image_file_id:, inline_callback:, message_id:, text:, user_id:, update_id:)
    @callback_query_id = callback_query_id
    @chat_id = chat_id
    @image_file_id = image_file_id
    @inline_callback = inline_callback
    @message_id = message_id
    @text = text
    @user_id = user_id
    @update_id = update_id
  end

  def inline_callback?
    @inline_callback
  end

  def unsupported?
    text.blank? && image_file_id.blank?
  end
end
