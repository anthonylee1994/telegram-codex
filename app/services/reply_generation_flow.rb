require "fileutils"

class ReplyGenerationFlow
  PDF_UNAVAILABLE_MESSAGE = "而家未開到 PDF 轉圖工具，所以暫時睇唔到 PDF。你可以改為 send screenshot，或者等我開通 PDF 支援。"
  TEXT_DOCUMENT_UNAVAILABLE_MESSAGE = "而家未開到 Office / 文字檔抽取工具，所以暫時睇唔到份檔案內容。你可以改為貼文字、send PDF，或者等我開通完整支援。"

  def initialize(
    conversation_service:,
    telegram_client:,
    pdf_page_rasterizer: PdfPageRasterizer.new(max_pages: AppConfig.fetch.max_pdf_pages),
    text_document_extractor: TextDocumentExtractor.new
  )
    @conversation_service = conversation_service
    @telegram_client = telegram_client
    @pdf_page_rasterizer = pdf_page_rasterizer
    @text_document_extractor = text_document_extractor
  end

  def call(message)
    has_pending_reply = false

    begin
      reply = @telegram_client.with_typing_status(message.chat_id) do
        image_file_paths = download_attachments_if_needed(message)
        text_override = build_text_override(message)

        begin
          build_reply(message, image_file_paths, text_override)
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
    rescue PdfPageRasterizer::MissingDependencyError => e
      @conversation_service.clear_processing(message.update_id) unless has_pending_reply
      Rails.logger.error("PDF conversion unavailable update_id=#{message.update_id} chat_id=#{message.chat_id} error=#{e.message}")
      @telegram_client.send_message(message.chat_id, PDF_UNAVAILABLE_MESSAGE)
    rescue TextDocumentExtractor::MissingDependencyError => e
      @conversation_service.clear_processing(message.update_id) unless has_pending_reply
      Rails.logger.error("Text document extraction unavailable update_id=#{message.update_id} chat_id=#{message.chat_id} error=#{e.message}")
      @telegram_client.send_message(message.chat_id, TEXT_DOCUMENT_UNAVAILABLE_MESSAGE)
    rescue StandardError
      @conversation_service.clear_processing(message.update_id) unless has_pending_reply
      raise if has_pending_reply

      raise
    end
  end

  private

  def build_reply(message, image_file_paths, text_override)
    @conversation_service.generate_reply(message, image_file_paths: image_file_paths, text_override: text_override)
  end

  def download_attachments_if_needed(message)
    image_file_paths = download_images_if_needed(message)
    return image_file_paths unless message.pdf?

    pdf_path = @telegram_client.download_file_to_temp(message.pdf_file_id)
    image_file_paths + @pdf_page_rasterizer.rasterize(pdf_path)
  end

  def build_text_override(message)
    return message.text unless message.text_document?

    document_path = @telegram_client.download_file_to_temp(message.text_document_file_id)
    extraction_result = @text_document_extractor.extract(document_path)
    base_prompt = if message.text.present?
      message.text
    else
      "我上載咗一份文字檔。請先概括內容，再按內容回答。"
    end

    [
      base_prompt,
      "",
      "檔案名稱：#{message.text_document_name.presence || '未命名檔案'}",
      "以下係檔案內容：",
      "```text",
      extraction_result.content,
      "```",
      extraction_result.truncated ? "注意：檔案內容已經截短，只包含前面一部分。" : nil
    ].compact.join("\n")
  end

  def download_images_if_needed(message)
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
