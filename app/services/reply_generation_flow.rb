require "fileutils"

class ReplyGenerationFlow
  def initialize(conversation_service:, telegram_client:)
    @conversation_service = conversation_service
    @telegram_client = telegram_client
  end

  def call(message)
    has_pending_reply = false

    begin
      reply = @telegram_client.with_typing_status(message.chat_id) do
        image_file_path = download_image_if_needed(message)

        begin
          build_reply(message, image_file_path)
        ensure
          cleanup_downloaded_image(image_file_path)
        end
      end

      @conversation_service.save_pending_reply(message.update_id, message.chat_id, message.message_id, reply)
      has_pending_reply = true
      @telegram_client.send_message(
        message.chat_id,
        reply.fetch(:text),
        suggested_replies: reply.fetch(:suggested_replies)
      )
      @conversation_service.persist_conversation_state(message.chat_id, reply.fetch(:conversation_state))
      @conversation_service.mark_processed(message.update_id, message.chat_id, message.message_id)
    rescue StandardError
      @conversation_service.clear_processing(message.update_id) unless has_pending_reply
      raise if has_pending_reply

      raise
    end
  end

  private

  def build_reply(message, image_file_path)
    generated_reply = @conversation_service.generate_reply(message, image_file_path: image_file_path)
    suggested_replies = @conversation_service.generate_suggested_replies(generated_reply.fetch(:conversation_state))

    generated_reply.merge(suggested_replies: suggested_replies)
  end

  def download_image_if_needed(message)
    return nil if message.image_file_id.blank?

    @telegram_client.download_file_to_temp(message.image_file_id)
  end

  def cleanup_downloaded_image(image_file_path)
    return if image_file_path.blank?

    FileUtils.rm_rf(File.dirname(image_file_path))
  end
end
