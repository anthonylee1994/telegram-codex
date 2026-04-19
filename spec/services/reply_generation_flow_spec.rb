require "rails_helper"

RSpec.describe ReplyGenerationFlow do
  let(:conversation_service) { instance_double(ConversationService) }
  let(:telegram_client) { instance_double(TelegramClient) }
  let(:pdf_page_rasterizer) { instance_double(PdfPageRasterizer) }
  let(:flow) do
    described_class.new(
      conversation_service: conversation_service,
      telegram_client: telegram_client,
      pdf_page_rasterizer: pdf_page_rasterizer
    )
  end

  it "downloads a pdf, rasterizes its pages, and sends the generated reply" do
    message = InboundTelegramMessage.new(
      chat_id: "3",
      image_file_ids: [],
      message_id: 2,
      pdf_file_id: "document-pdf-file",
      text: "幫我睇呢份 PDF",
      user_id: "234392020",
      update_id: 1
    )
    allow(telegram_client).to receive(:with_typing_status).and_yield
    allow(telegram_client).to receive(:download_file_to_temp).with("document-pdf-file").and_return("/tmp/report.pdf")
    allow(pdf_page_rasterizer).to receive(:rasterize).with("/tmp/report.pdf").and_return(["/tmp/page-1.png", "/tmp/page-2.png"])
    allow(conversation_service).to receive(:generate_reply).and_return(
      conversation_state: "state-1",
      suggested_replies: ["下一步", "列重點", "講結論"],
      text: "pdf reply"
    )
    allow(conversation_service).to receive(:save_pending_reply)
    allow(conversation_service).to receive(:persist_conversation_state)
    allow(conversation_service).to receive(:mark_processed)
    allow(telegram_client).to receive(:send_message)
    allow(FileUtils).to receive(:rm_rf)

    flow.call(message)

    expect(conversation_service).to have_received(:generate_reply).with(
      message,
      image_file_paths: ["/tmp/page-1.png", "/tmp/page-2.png"]
    )
    expect(telegram_client).to have_received(:send_message).with(
      "3",
      "pdf reply",
      suggested_replies: ["下一步", "列重點", "講結論"]
    )
  end

  it "sends a specific message when pdf conversion tools are unavailable" do
    message = InboundTelegramMessage.new(
      chat_id: "3",
      image_file_ids: [],
      message_id: 2,
      pdf_file_id: "document-pdf-file",
      text: "幫我睇呢份 PDF",
      user_id: "234392020",
      update_id: 1
    )
    allow(telegram_client).to receive(:with_typing_status).and_yield
    allow(telegram_client).to receive(:download_file_to_temp).with("document-pdf-file").and_return("/tmp/report.pdf")
    allow(pdf_page_rasterizer).to receive(:rasterize).and_raise(PdfPageRasterizer::MissingDependencyError, "pdftoppm is not installed")
    allow(conversation_service).to receive(:clear_processing)
    allow(telegram_client).to receive(:send_message)
    allow(Rails.logger).to receive(:error)

    flow.call(message)

    expect(telegram_client).to have_received(:send_message).with("3", ReplyGenerationFlow::PDF_UNAVAILABLE_MESSAGE)
    expect(conversation_service).to have_received(:clear_processing).with(1)
  end
end
