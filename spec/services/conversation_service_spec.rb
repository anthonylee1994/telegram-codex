# frozen_string_literal: true

require 'rails_helper'

RSpec.describe ConversationService do
  let(:reply_client) { instance_double(CodexCliClient) }
  let(:service) { described_class.new(reply_client: reply_client) }

  describe '#generate_reply' do
    it 'passes through conversation state for an active session' do
      ChatSession.create!(chat_id: 'chat-1', last_response_id: 'state-old', updated_at: current_time_ms)
      result = { conversation_state: 'state-new', text: 'reply' }

      allow(reply_client).to receive(:generate_reply).and_return(result)

      reply = service.generate_reply(
        chat_id: 'chat-1',
        image_file_id: nil,
        message_id: 10,
        text: 'hello',
        update_id: 100,
        user_id: '234392020'
      )

      expect(reply).to eq(result)
      expect(reply_client).to have_received(:generate_reply).with(
        chat_id: 'chat-1',
        text: 'hello',
        conversation_state: 'state-old',
        image_file_path: nil
      )
    end

    it 'resets expired sessions before generating a reply' do
      ChatSession.create!(chat_id: 'chat-1', last_response_id: 'state-old', updated_at: current_time_ms - 100_000)
      allow(reply_client).to receive(:generate_reply).and_return(conversation_state: 'state-new', text: 'new reply')
      allow(AppConfig).to receive(:fetch).and_return(
        AppConfig::Config.new(
          allowed_telegram_user_ids: [],
          base_url: 'https://example.com',
          port: 3000,
          rate_limit_max_messages: 5,
          rate_limit_window_ms: 10_000,
          session_ttl_days: 1.0 / 86_400,
          sqlite_db_path: Rails.root.join('data/test.db').to_s,
          telegram_bot_token: 'token',
          telegram_webhook_secret: 'secret'
        )
      )

      service.generate_reply(
        chat_id: 'chat-1',
        image_file_id: nil,
        message_id: 10,
        text: 'hello',
        update_id: 100,
        user_id: '234392020'
      )

      expect(reply_client).to have_received(:generate_reply).with(
        chat_id: 'chat-1',
        text: 'hello',
        conversation_state: nil,
        image_file_path: nil
      )
    end
  end

  def current_time_ms
    (Time.now.to_f * 1000).to_i
  end
end
