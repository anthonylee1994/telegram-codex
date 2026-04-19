require 'rails_helper'

RSpec.describe SessionSummaryJob do
  let(:conversation_service) { instance_double(ConversationService) }
  let(:telegram_client) { instance_double(TelegramClient) }

  before do
    allow(ConversationService).to receive(:new).and_return(conversation_service)
    allow(TelegramClient).to receive(:new).and_return(telegram_client)
    allow(telegram_client).to receive(:send_message)
  end

  it 'sends the compressed summary result back to the chat' do
    allow(conversation_service).to receive(:summarize_session).with('3').and_return(
      status: :ok,
      original_message_count: 8,
      summary_text: '重點：支援 PDF，同埋要加 /summary。'
    )

    perform_enqueued_jobs do
      described_class.perform_later('3')
    end

    expect(telegram_client).to have_received(:send_message).with(
      '3',
      include('已經將目前 session 壓縮成新 context。'),
      remove_keyboard: true
    )
  end

  it 'sends a timeout-specific fallback when retries are exhausted' do
    allow(conversation_service).to receive(:summarize_session).and_raise(
      CodexExecRunner::ExecutionTimeoutError,
      'timeout'
    )

    perform_enqueued_jobs do
      described_class.perform_later('3')
    end

    expect(telegram_client).to have_received(:send_message).with(
      '3',
      described_class::TIMEOUT_ERROR_MESSAGE,
      remove_keyboard: true
    )
  end
end
