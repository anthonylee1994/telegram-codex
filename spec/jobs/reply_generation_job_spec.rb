require "rails_helper"

RSpec.describe ReplyGenerationJob do
  let(:telegram_client) { instance_double(TelegramClient, download_file_to_temp: nil) }
  let(:exec_runner) { instance_double(CodexExecRunner) }
  let(:message) do
    InboundTelegramMessage.new(
      chat_id: "3",
      image_file_ids: [],
      message_id: 2,
      text: "hello",
      user_id: "234392020",
      update_id: 1
    )
  end

  before do
    allow(TelegramClient).to receive(:new).and_return(telegram_client)
    allow(CodexExecRunner).to receive(:new).and_return(exec_runner)
    allow(telegram_client).to receive(:with_typing_status).and_yield
  end

  it "generates the reply in the background and persists the result" do
    allow(exec_runner).to receive(:run).and_return(
      '{"text":"reply-1","suggested_replies":["下一步可以點做？","幫我列重點。","可唔可以講詳細啲？"]}'
    )
    allow(telegram_client).to receive(:send_message)

    described_class.perform_now(message.to_job_payload)

    expect(exec_runner).to have_received(:run).once
    expect(telegram_client).to have_received(:send_message).with(
      "3",
      "reply-1",
      suggested_replies: ["下一步可以點做？", "幫我列重點。", "可唔可以講詳細啲？"]
    )
    expect(ChatSession.find_by(chat_id: "3")&.last_response_id).to be_present
    expect(ProcessedUpdate.find_by(update_id: 1)&.sent_at).to be_present
  end

  it "replays a persisted pending reply without re-running codex" do
    conversation_service = ConversationService.new(reply_client: instance_double(CodexCliClient))
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

    expect(telegram_client).to have_received(:send_message).with("3", TelegramWebhookHandler::GENERIC_ERROR_MESSAGE)
    expect(ProcessedUpdate.find_by(update_id: 1)).to be_nil
  end

  it "sends a specific timeout fallback after retries are exhausted" do
    allow(exec_runner).to receive(:run).and_raise(CodexExecRunner::ExecutionTimeoutError, "codex exec timed out after 90 seconds")
    allow(telegram_client).to receive(:send_message)

    perform_enqueued_jobs do
      expect {
        described_class.perform_later(message.to_job_payload)
      }.not_to raise_error
    end

    expect(telegram_client).to have_received(:send_message).with("3", ReplyGenerationJob::TIMEOUT_ERROR_MESSAGE)
    expect(ProcessedUpdate.find_by(update_id: 1)).to be_nil
  end
end
