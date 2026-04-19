require 'rails_helper'

RSpec.describe ConversationService do
  let(:reply_client) { instance_double(CodexCliClient) }
  let(:session_summary_client) { instance_double(SessionSummaryClient) }
  let(:service) { described_class.new(reply_client: reply_client, session_summary_client: session_summary_client) }
  let(:message) do
    InboundTelegramMessage.new(
      chat_id: 'chat-1',
      image_file_ids: [],
      message_id: 10,
      text: 'hello',
      user_id: '234392020',
      update_id: 100
    )
  end

  before do
    described_class.reset_prune_state!
  end

  describe '#generate_reply' do
    it 'passes through conversation state for an active session' do
      ChatSession.create!(chat_id: 'chat-1', last_response_id: 'state-old', updated_at: current_time_ms)
      result = { conversation_state: 'state-new', text: 'reply' }

      allow(reply_client).to receive(:generate_reply).and_return(result)

      reply = service.generate_reply(message)

      expect(reply).to eq(result)
      expect(reply_client).to have_received(:generate_reply).with(
        chat_id: 'chat-1',
        text: 'hello',
        conversation_state: 'state-old',
        image_file_paths: []
      )
    end

    it 'resets expired sessions before generating a reply' do
      ChatSession.create!(chat_id: 'chat-1', last_response_id: 'state-old', updated_at: current_time_ms - 100_000)
      allow(reply_client).to receive(:generate_reply).and_return(
        conversation_state: 'state-new',
        text: 'new reply'
      )
      allow(AppConfig).to receive(:fetch).and_return(
        AppConfig::Config.new(
          allowed_telegram_user_ids: [],
          base_url: 'https://example.com',
          codex_exec_timeout_seconds: 300,
          max_media_group_images: 6,
          max_pdf_pages: 4,
          media_group_wait_ms: 1200,
          port: 3000,
          rate_limit_max_messages: 5,
          rate_limit_window_ms: 10_000,
          session_ttl_days: 1.0 / 86_400,
          sqlite_db_path: Rails.root.join('data/test.db').to_s,
          telegram_bot_token: 'token',
          telegram_webhook_secret: 'secret'
        )
      )

      service.generate_reply(message)

      expect(reply_client).to have_received(:generate_reply).with(
        chat_id: 'chat-1',
        text: 'hello',
        conversation_state: nil,
        image_file_paths: []
      )
    end

    it 'passes text override through to the reply client when provided' do
      allow(reply_client).to receive(:generate_reply).and_return(
        conversation_state: 'state-new',
        text: 'reply'
      )

      service.generate_reply(message, text_override: 'override text')

      expect(reply_client).to have_received(:generate_reply).with(
        chat_id: 'chat-1',
        text: 'override text',
        conversation_state: nil,
        image_file_paths: []
      )
    end

    it 'prunes old processed updates that were already sent' do
      old_processed_at = current_time_ms - (31 * 24 * 60 * 60 * 1000)

      ProcessedUpdate.create!(
        update_id: 1,
        chat_id: 'chat-1',
        message_id: 1,
        processed_at: old_processed_at,
        sent_at: old_processed_at
      )
      ProcessedUpdate.create!(
        update_id: 2,
        chat_id: 'chat-1',
        message_id: 2,
        processed_at: old_processed_at,
        sent_at: nil
      )
      ProcessedUpdate.create!(
        update_id: 3,
        chat_id: 'chat-1',
        message_id: 3,
        processed_at: current_time_ms,
        sent_at: current_time_ms
      )

      allow(reply_client).to receive(:generate_reply).and_return(
        conversation_state: 'state-new',
        text: 'reply'
      )

      service.generate_reply(message)

      expect(ProcessedUpdate.find_by(update_id: 1)).to be_nil
      expect(ProcessedUpdate.find_by(update_id: 2)).to be_present
      expect(ProcessedUpdate.find_by(update_id: 3)).to be_present
    end

    it 'does not prune more than once per interval' do
      old_processed_at = current_time_ms - (31 * 24 * 60 * 60 * 1000)

      ProcessedUpdate.create!(
        update_id: 1,
        chat_id: 'chat-1',
        message_id: 1,
        processed_at: old_processed_at,
        sent_at: old_processed_at
      )

      allow(reply_client).to receive(:generate_reply).and_return(
        conversation_state: 'state-new',
        text: 'reply'
      )

      service.generate_reply(message)

      ProcessedUpdate.create!(
        update_id: 2,
        chat_id: 'chat-1',
        message_id: 2,
        processed_at: old_processed_at,
        sent_at: old_processed_at
      )

      service.generate_reply(
        InboundTelegramMessage.new(
          chat_id: 'chat-1',
          image_file_ids: [],
          message_id: 11,
          text: 'hello again',
          user_id: '234392020',
          update_id: 101
        )
      )

      expect(ProcessedUpdate.find_by(update_id: 2)).to be_present
    end
  end

  describe '#session_snapshot' do
    it 'returns inactive when there is no active session' do
      expect(service.session_snapshot('chat-1')).to eq(active: false)
    end

    it 'returns active session metadata' do
      transcript = [
        { role: 'user', content: '你好' },
        { role: 'assistant', content: '你好，有咩幫到你？' },
        { role: 'user', content: '幫我整理' }
      ]
      ChatSession.create!(
        chat_id: 'chat-1',
        last_response_id: JSON.generate(transcript),
        updated_at: current_time_ms
      )

      snapshot = service.session_snapshot('chat-1')

      expect(snapshot[:active]).to be(true)
      expect(snapshot[:message_count]).to eq(3)
      expect(snapshot[:turn_count]).to eq(2)
      expect(snapshot[:last_updated_at]).to be_a(ActiveSupport::TimeWithZone)
    end
  end

  describe '#summarize_session' do
    it 'returns missing_session when there is no active session' do
      expect(service.summarize_session('chat-1')).to eq(status: :missing_session)
    end

    it 'returns too_short when the transcript is too short' do
      ChatSession.create!(
        chat_id: 'chat-1',
        last_response_id: JSON.generate([
          { role: 'user', content: '你好' },
          { role: 'assistant', content: '你好' },
          { role: 'user', content: '再講' }
        ]),
        updated_at: current_time_ms
      )

      expect(service.summarize_session('chat-1')).to eq(status: :too_short, message_count: 3)
    end

    it 'persists a compressed summary transcript for a long session' do
      ChatSession.create!(
        chat_id: 'chat-1',
        last_response_id: JSON.generate([
          { role: 'user', content: '我想整 Telegram bot' },
          { role: 'assistant', content: '可以，講下需求。' },
          { role: 'user', content: '要支援 PDF' },
          { role: 'assistant', content: '收到。' }
        ]),
        updated_at: current_time_ms
      )
      allow(session_summary_client).to receive(:summarize).and_return('用戶想整 Telegram bot，並且要支援 PDF。')

      result = service.summarize_session('chat-1')
      persisted_transcript = CodexTranscript.from_conversation_state(ChatSession.find('chat-1').last_response_id)

      expect(result).to eq(
        status: :ok,
        original_message_count: 4,
        summary_text: '用戶想整 Telegram bot，並且要支援 PDF。'
      )
      expect(session_summary_client).to have_received(:summarize).with(instance_of(CodexTranscript))
      expect(persisted_transcript.size).to eq(2)
      expect(persisted_transcript.messages.last).to eq(
        'role' => 'assistant',
        'content' => '用戶想整 Telegram bot，並且要支援 PDF。'
      )
    end
  end

  describe '#save_pending_reply' do
    it 'persists the main reply for resend' do
      service.save_pending_reply(
        100,
        'chat-1',
        10,
        {
          conversation_state: 'state-new',
          text: 'reply'
        }
      )

      processed_update = ProcessedUpdate.find(100)
      expect(processed_update.reply_text).to eq('reply')
      expect(processed_update.conversation_state).to eq('state-new')
    end

    it 'keeps the persisted reply when the same update is later marked as sent' do
      service.save_pending_reply(
        100,
        'chat-1',
        10,
        {
          conversation_state: 'state-new',
          suggested_replies: ['列重點', '下一步'],
          text: 'reply'
        }
      )

      service.mark_processed(100, 'chat-1', 10)

      processed_update = ProcessedUpdate.find(100)
      expect(processed_update.reply_text).to eq('reply')
      expect(processed_update.conversation_state).to eq('state-new')
      expect(JSON.parse(processed_update.suggested_replies)).to eq(['列重點', '下一步'])
      expect(processed_update.sent_at).to be_present
    end
  end

  describe '#begin_processing' do
    it 'claims a fresh update only once until it is cleared' do
      expect(service.begin_processing(100, 'chat-1', 10)).to be(true)
      expect(service.begin_processing(100, 'chat-1', 10)).to be(false)

      service.clear_processing(100)

      expect(service.begin_processing(100, 'chat-1', 10)).to be(true)
    end
  end

  def current_time_ms
    (Time.now.to_f * 1000).to_i
  end
end
