require "rails_helper"

RSpec.describe TelegramWebhookHandlerFactory do
  let(:telegram_client) { instance_double(TelegramClient, download_file_to_temp: nil) }
  let(:exec_runner) { instance_double(CodexExecRunner) }
  let(:update) do
    {
      "update_id" => 1,
      "message" => {
        "from" => {
          "id" => 234_392_020
        },
        "message_id" => 2,
        "text" => "hello",
        "chat" => {
          "id" => 3
        }
      }
    }
  end

  before do
    allow(TelegramClient).to receive(:new).and_return(telegram_client)
    allow(CodexExecRunner).to receive(:new).and_return(exec_runner)
    allow(telegram_client).to receive(:with_typing_status).and_yield
  end

  it "wires the full webhook flow and persists reply state" do
    allow(exec_runner).to receive(:run).and_return(
      '{"text":"reply-1","suggested_replies":["下一步可以點做？","幫我列重點。","可唔可以講詳細啲？"]}'
    )
    allow(telegram_client).to receive(:send_message)

    handler = described_class.build
    handler.handle(update)

    expect(exec_runner).to have_received(:run).once
    expect(telegram_client).to have_received(:send_message).with(
      "3",
      "reply-1",
      suggested_replies: [ "下一步可以點做？", "幫我列重點。", "可唔可以講詳細啲？" ]
    )
    expect(ChatSession.find_by(chat_id: "3")&.last_response_id).to be_present

    processed_update = ProcessedUpdate.find_by(update_id: 1)
    expect(processed_update&.reply_text).to eq("reply-1")
    expect(JSON.parse(processed_update&.suggested_replies)).to eq([ "下一步可以點做？", "幫我列重點。", "可唔可以講詳細啲？" ])
    expect(processed_update&.sent_at).to be_present
  end

  it "replays a pending reply without re-running codex" do
    attempt = 0
    allow(exec_runner).to receive(:run).and_return(
      '{"text":"reply-1","suggested_replies":["下一步可以點做？","幫我列重點。","可唔可以講詳細啲？"]}'
    )
    allow(telegram_client).to receive(:send_message) do |chat_id, text, suggested_replies: [], remove_keyboard: false|
      attempt += 1 if text == "reply-1"
      raise StandardError, "telegram send failed" if text == "reply-1" && attempt == 1
    end

    handler = described_class.build

    expect { handler.handle(update) }.to raise_error(StandardError, "telegram send failed")
    expect { handler.handle(update) }.not_to raise_error

    expect(exec_runner).to have_received(:run).once
    expect(telegram_client).to have_received(:send_message).with(
      "3",
      "reply-1",
      suggested_replies: [ "下一步可以點做？", "幫我列重點。", "可唔可以講詳細啲？" ]
    ).twice
    expect(ProcessedUpdate.find_by(update_id: 1)&.sent_at).to be_present
  end
end
