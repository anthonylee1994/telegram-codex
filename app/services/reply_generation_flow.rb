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
        image_file_paths = download_images_if_needed(message)

        begin
          build_reply(message, image_file_paths)
        ensure
          cleanup_downloaded_images(image_file_paths)
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

  def build_reply(message, image_file_paths)
    @conversation_service.generate_reply(message, image_file_paths: image_file_paths)
  end

  def download_images_if_needed(message)
    return [] if message.image_file_ids.empty?

    message.image_file_ids.map do |image_file_id|
      @telegram_client.download_file_to_temp(image_file_id)
    end
  end

  def cleanup_downloaded_images(image_file_paths)
    Array(image_file_paths).map { |image_file_path| File.dirname(image_file_path) }.uniq.each do |directory_path|
      FileUtils.rm_rf(directory_path)
    end
  end
end
