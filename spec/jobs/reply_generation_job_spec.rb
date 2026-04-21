require "rails_helper"

RSpec.describe ReplyGenerationJob do
  let(:telegram_client) { instance_double(Telegram::Client, download_file_to_temp: nil) }
  let(:exec_runner) { instance_double(Codex::ExecRunner) }
  let(:message) do
    Telegram::InboundMessage.new(
      chat_id: "3",
      image_file_ids: [],
      message_id: 2,
      text: "hello",
      user_id: "234392020",
      update_id: 1
    )
  end
  let(:pdf_page_rasterizer) { instance_double(Documents::PdfPageRasterizer) }
  let(:text_document_extractor) { instance_double(Documents::TextDocumentExtractor) }

  before do
    allow(Telegram::Client).to receive(:new).and_return(telegram_client)
    allow(Codex::ExecRunner).to receive(:new).and_return(exec_runner)
    allow(Documents::PdfPageRasterizer).to receive(:new).and_return(pdf_page_rasterizer)
    allow(Documents::TextDocumentExtractor).to receive(:new).and_return(text_document_extractor)
    allow(telegram_client).to receive(:with_typing_status).and_yield
    allow(pdf_page_rasterizer).to receive(:rasterize).and_return([])
    allow(text_document_extractor).to receive(:extract)
  end

  it "generates the reply in the background and persists the result" do
    allow(exec_runner).to receive(:run).and_return(
      '{"text":"reply-1","suggested_replies":["下一步可以點做？","幫我列重點。","可唔可以講詳細啲？"]}'
    )
    allow(telegram_client).to receive(:send_message)

    described_class.perform_now(message.to_job_payload)

    expect(exec_runner).to have_received(:run).at_least(:once)
    expect(telegram_client).to have_received(:send_message).with(
      "3",
      "reply-1",
      suggested_replies: ["下一步可以點做？", "幫我列重點。", "可唔可以講詳細啲？"]
    )
    expect(ChatSession.find_by(chat_id: "3")&.last_response_id).to be_present
    expect(ProcessedUpdate.find_by(update_id: 1)&.sent_at).to be_present
  end

  it "replays a persisted pending reply without re-running codex" do
    conversation_service = Conversation::Service.new(reply_client: instance_double(Codex::CliClient))
    conversation_service.save_pending_reply(
      1,
      "3",
      2,
      {
        conversation_state: "state-1",
        suggested_replies: ["下一步可以點做？", "幫我列重點。", "可唔可以講詳細啲？"],
        text: "reply-1"
      }
    )
    allow(exec_runner).to receive(:run)
    allow(telegram_client).to receive(:send_message)

    described_class.perform_now(message.to_job_payload)

    expect(exec_runner).not_to have_received(:run)
    expect(telegram_client).to have_received(:send_message).with(
      "3",
      "reply-1",
      suggested_replies: ["下一步可以點做？", "幫我列重點。", "可唔可以講詳細啲？"]
    )
    expect(ProcessedUpdate.find_by(update_id: 1)&.sent_at).to be_present
  end

  it "sends the generic fallback after retries are exhausted" do
    allow(exec_runner).to receive(:run).and_raise(StandardError, "codex down")
    allow(telegram_client).to receive(:send_message)

    perform_enqueued_jobs do
      expect {
        described_class.perform_later(message.to_job_payload)
      }.not_to raise_error
    end

    expect(telegram_client).to have_received(:send_message).with("3", Telegram::WebhookHandler::GENERIC_ERROR_MESSAGE)
    expect(ProcessedUpdate.find_by(update_id: 1)).to be_nil
  end

  it "sends a specific timeout fallback after retries are exhausted" do
    allow(exec_runner).to receive(:run).and_raise(Codex::ExecRunner::ExecutionTimeoutError, "codex exec timed out after 90 seconds")
    allow(telegram_client).to receive(:send_message)

    perform_enqueued_jobs do
      expect {
        described_class.perform_later(message.to_job_payload)
      }.not_to raise_error
    end

    expect(telegram_client).to have_received(:send_message).with("3", ReplyGenerationJob::TIMEOUT_ERROR_MESSAGE)
    expect(ProcessedUpdate.find_by(update_id: 1)).to be_nil
  end

  it "downloads pdf documents, rasterizes pages, and sends them to codex as images" do
    pdf_message = Telegram::InboundMessage.new(
      chat_id: "3",
      image_file_ids: [],
      message_id: 2,
      pdf_file_id: "document-pdf-file",
      text: "幫我睇呢份 PDF",
      user_id: "234392020",
      update_id: 1
    )
    allow(telegram_client).to receive(:download_file_to_temp).with("document-pdf-file").and_return("/tmp/report.pdf")
    allow(pdf_page_rasterizer).to receive(:rasterize).with("/tmp/report.pdf").and_return(["/tmp/page-1.png", "/tmp/page-2.png"])
    allow(exec_runner).to receive(:run).and_return(
      '{"text":"reply-1","suggested_replies":["下一步可以點做？","幫我列重點。","可唔可以講詳細啲？"]}'
    )
    allow(telegram_client).to receive(:send_message)

    described_class.perform_now(pdf_message.to_job_payload)

    expect(exec_runner).to have_received(:run).with(
      prompt: kind_of(String),
      image_file_paths: ["/tmp/page-1.png", "/tmp/page-2.png"],
      output_schema: kind_of(Hash)
    )
  end

  it "downloads text documents, extracts their content, and passes them as prompt text" do
    text_message = Telegram::InboundMessage.new(
      chat_id: "3",
      image_file_ids: [],
      message_id: 2,
      text: "幫我列重點",
      text_document_file_id: "document-text-file",
      text_document_name: "notes.txt",
      user_id: "234392020",
      update_id: 1
    )
    allow(telegram_client).to receive(:download_file_to_temp).with("document-text-file").and_return("/tmp/notes.txt")
    allow(text_document_extractor).to receive(:extract).with("/tmp/notes.txt").and_return(
      Documents::TextDocumentExtractor::ExtractionResult.new(content: "alpha\nbeta", truncated: false)
    )
    allow(exec_runner).to receive(:run).and_return(
      '{"text":"reply-1","suggested_replies":["下一步可以點做？","幫我列重點。","可唔可以講詳細啲？"]}'
    )
    allow(telegram_client).to receive(:send_message)

    described_class.perform_now(text_message.to_job_payload)

    expect(exec_runner).to have_received(:run).with(
      prompt: include("檔案名稱：notes.txt", "alpha\nbeta"),
      image_file_paths: [],
      output_schema: kind_of(Hash)
    )
  end

  it "downloads a quoted pdf document and sends it to codex as images" do
    quoted_pdf_message = Telegram::InboundMessage.new(
      chat_id: "3",
      image_file_ids: [],
      message_id: 2,
      reply_to_message_id: 1,
      reply_to_pdf_file_id: "quoted-pdf-file",
      reply_to_text: "用戶引用咗一份 PDF。",
      text: "幫我總結返",
      user_id: "234392020",
      update_id: 1
    )
    allow(telegram_client).to receive(:download_file_to_temp).with("quoted-pdf-file").and_return("/tmp/quoted.pdf")
    allow(pdf_page_rasterizer).to receive(:rasterize).with("/tmp/quoted.pdf").and_return(["/tmp/quoted-page-1.png"])
    allow(exec_runner).to receive(:run).and_return(
      '{"text":"reply-1","suggested_replies":["下一步可以點做？","幫我列重點。","可唔可以講詳細啲？"]}'
    )
    allow(telegram_client).to receive(:send_message)

    described_class.perform_now(quoted_pdf_message.to_job_payload)

    expect(exec_runner).to have_received(:run).with(
      prompt: include("被引用訊息：用戶引用咗一份 PDF。"),
      image_file_paths: ["/tmp/quoted-page-1.png"],
      output_schema: kind_of(Hash)
    )
  end
end
