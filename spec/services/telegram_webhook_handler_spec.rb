require 'rails_helper'

RSpec.describe TelegramWebhookHandler do
  let(:reply_client) { instance_double(CodexCliClient) }
  let(:conversation_service) { ConversationService.new(reply_client: reply_client) }
  let(:telegram_client) { instance_double(TelegramClient, download_file_to_temp: nil) }
  let(:config) do
    AppConfig::Config.new(
      allowed_telegram_user_ids: [],
      base_url: 'https://example.com',
      port: 3000,
      rate_limit_max_messages: 5,
      rate_limit_window_ms: 10_000,
      session_ttl_days: 7,
      sqlite_db_path: Rails.root.join('data/test.db').to_s,
      telegram_bot_token: 'token',
      telegram_webhook_secret: 'secret'
    )
  end
  let(:handler) do
    described_class.new(
      conversation_service: conversation_service,
      telegram_client: telegram_client,
      rate_limiter: ChatRateLimiter.instance,
      telegram_update_parser: TelegramUpdateParser.new,
      config: config
    )
  end
  let(:update) do
    {
      'update_id' => 1,
      'message' => {
        'from' => {
          'id' => 234_392_020
        },
        'message_id' => 2,
        'text' => 'hello',
        'chat' => {
          'id' => 3
        }
      }
    }
  end
  let(:callback_update) do
    {
      'update_id' => 9,
      'callback_query' => {
        'id' => 'callback-1',
        'data' => '再濃縮',
        'from' => {
          'id' => 234_392_020
        },
        'message' => {
          'message_id' => 7,
          'chat' => {
            'id' => 3
          }
        }
      }
    }
  end

  it 're-sends a persisted pending reply without regenerating it' do
    attempt = 0

    allow(reply_client).to receive(:generate_reply).and_return(
      conversation_state: 'state-1',
      text: 'reply-1'
    )
    allow(reply_client).to receive(:generate_suggested_replies).and_return([ '下一步可以點做？', '幫我列重點。', '可唔可以講詳細啲？' ])
    allow(telegram_client).to receive(:with_typing_status).and_yield
    allow(telegram_client).to receive(:send_message).with('3', 'reply-1') do
      attempt += 1
      raise StandardError, 'telegram send failed' if attempt == 1
    end
    allow(telegram_client).to receive(:send_message).with(
      '3',
      TelegramWebhookHandler::CONTINUE_PROMPT_MESSAGE,
      suggested_replies: [ '下一步可以點做？', '幫我列重點。', '可唔可以講詳細啲？' ]
    )

    expect { handler.handle(update) }.to raise_error(StandardError, 'telegram send failed')
    expect { handler.handle(update) }.not_to raise_error

    expect(reply_client).to have_received(:generate_reply).once
    expect(telegram_client).to have_received(:send_message).with('3', 'reply-1').twice
    expect(ChatSession.find_by(chat_id: '3')&.last_response_id).to eq('state-1')
    expect(ProcessedUpdate.find_by(update_id: 1)&.reply_text).to eq('reply-1')
    expect(ProcessedUpdate.find_by(update_id: 1)&.suggested_replies).to be_nil
    expect(ProcessedUpdate.find_by(update_id: 1)&.sent_at).to be_present
  end

  it 'sends the answer first and suggested replies after' do
    call_order = []

    allow(reply_client).to receive(:generate_reply).and_return(
      conversation_state: 'state-1',
      text: 'reply-1'
    )
    allow(reply_client).to receive(:generate_suggested_replies).and_return([ '下一步可以點做？', '幫我列重點。', '可唔可以講詳細啲？' ])
    allow(telegram_client).to receive(:with_typing_status).and_yield
    allow(telegram_client).to receive(:send_message) do |chat_id, text, suggested_replies: []|
      call_order << [ chat_id, text, suggested_replies ]
    end

    handler.handle(update)

    expect(call_order).to eq([
      [ '3', 'reply-1', [] ],
      [ '3', TelegramWebhookHandler::CONTINUE_PROMPT_MESSAGE, [ '下一步可以點做？', '幫我列重點。', '可唔可以講詳細啲？' ] ]
    ])
  end

  it 'does not fail the main reply when suggested replies fail' do
    allow(reply_client).to receive(:generate_reply).and_return(
      conversation_state: 'state-1',
      text: 'reply-1'
    )
    allow(reply_client).to receive(:generate_suggested_replies).and_raise(StandardError, 'slow fail')
    allow(telegram_client).to receive(:with_typing_status).and_yield
    allow(telegram_client).to receive(:send_message)

    expect { handler.handle(update) }.not_to raise_error
    expect(telegram_client).to have_received(:send_message).with('3', 'reply-1')
  end

  it 'answers callback queries and treats the button text as a new message' do
    allow(reply_client).to receive(:generate_reply).and_return(
      conversation_state: 'state-2',
      text: 'reply-from-button'
    )
    allow(reply_client).to receive(:generate_suggested_replies).and_return([ '再直接啲', '舉個例', '改短啲' ])
    allow(telegram_client).to receive(:answer_callback_query)
    allow(telegram_client).to receive(:with_typing_status).and_yield
    allow(telegram_client).to receive(:send_message)

    handler.handle(callback_update)

    expect(telegram_client).to have_received(:answer_callback_query).with('callback-1')
    expect(reply_client).to have_received(:generate_reply).with(
      chat_id: '3',
      text: '再濃縮',
      conversation_state: nil,
      image_file_path: nil
    )
  end

  it 'resets session and replies with start message for /start' do
    ChatSession.create!(chat_id: '3', last_response_id: 'state-old', updated_at: current_time_ms)
    start_update = update.deep_merge('message' => { 'text' => '/start' })

    allow(telegram_client).to receive(:send_message)

    handler.handle(start_update)

    expect(ChatSession.find_by(chat_id: '3')).to be_nil
    expect(telegram_client).to have_received(:send_message).with('3', TelegramWebhookHandler::START_MESSAGE)
  end

  def current_time_ms
    (Time.now.to_f * 1000).to_i
  end
end
