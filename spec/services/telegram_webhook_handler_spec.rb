require 'rails_helper'

RSpec.describe TelegramWebhookHandler do
  let(:reply_client) { instance_double(CodexCliClient) }
  let(:telegram_client) { instance_double(TelegramClient, download_file_to_temp: nil) }
  let(:config) { telegram_test_config }
  let(:handler_bundle) { build_telegram_webhook_handler(reply_client: reply_client, telegram_client: telegram_client, config: config) }
  let(:handler) { handler_bundle.first }
  let(:conversation_service) { handler_bundle.last }
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
  let(:start_callback_update) do
    callback_update.deep_merge(
      'callback_query' => {
        'data' => '/start',
        'message' => {
          'message_id' => 8
        }
      }
    )
  end
  let(:new_callback_update) do
    callback_update.deep_merge(
      'callback_query' => {
        'data' => '/new',
        'message' => {
          'message_id' => 9
        }
      }
    )
  end
  let(:show_memory_update) do
    update.deep_merge('message' => { 'text' => '/show_memory' })
  end
  let(:clear_memory_update) do
    update.deep_merge('message' => { 'text' => '/clear_memory' })
  end

  it 're-sends a persisted pending reply without regenerating it' do
    attempt = 0
    suggested_replies = [ '下一步可以點做？', '幫我列重點。', '可唔可以講詳細啲？' ]

    allow(reply_client).to receive(:generate_reply).and_return(
      conversation_state: 'state-1',
      text: 'reply-1'
    )
    allow(reply_client).to receive(:generate_suggested_replies).and_return(suggested_replies)
    allow(telegram_client).to receive(:with_typing_status).and_yield
    allow(telegram_client).to receive(:send_message).with('3', 'reply-1', suggested_replies: suggested_replies) do
      attempt += 1
      raise StandardError, 'telegram send failed' if attempt == 1
    end

    expect { handler.handle(update) }.to raise_error(StandardError, 'telegram send failed')
    expect { handler.handle(update) }.not_to raise_error

    expect(reply_client).to have_received(:generate_reply).once
    expect(reply_client).to have_received(:generate_suggested_replies).once
    expect(telegram_client).to have_received(:send_message).with('3', 'reply-1', suggested_replies: suggested_replies).twice
    expect(ChatSession.find_by(chat_id: '3')&.last_response_id).to eq('state-1')
    expect(ProcessedUpdate.find_by(update_id: 1)&.reply_text).to eq('reply-1')
    expect(JSON.parse(ProcessedUpdate.find_by(update_id: 1)&.suggested_replies)).to eq(suggested_replies)
    expect(ProcessedUpdate.find_by(update_id: 1)&.sent_at).to be_present
  end

  it 'ignores an update that was already marked as sent' do
    conversation_service.mark_processed(1, '3', 2)

    allow(reply_client).to receive(:generate_reply)
    allow(telegram_client).to receive(:send_message)

    handler.handle(update)

    expect(telegram_client).not_to have_received(:send_message)
    expect(reply_client).not_to have_received(:generate_reply)
  end

  it 'generates reply and suggestions before sending once' do
    call_order = []
    suggested_replies = [ '下一步可以點做？', '幫我列重點。', '可唔可以講詳細啲？' ]

    allow(reply_client).to receive(:generate_reply).and_return(
      conversation_state: 'state-1',
      text: 'reply-1'
    )
    allow(reply_client).to receive(:generate_suggested_replies) do
      call_order << :generate_suggested_replies
      suggested_replies
    end
    allow(telegram_client).to receive(:with_typing_status).and_yield
    allow(telegram_client).to receive(:send_message) do |chat_id, text, suggested_replies: []|
      call_order << [ :send_message, chat_id, text, suggested_replies ]
    end

    handler.handle(update)

    expect(call_order).to eq([
      :generate_suggested_replies,
      [ :send_message, '3', 'reply-1', suggested_replies ]
    ])
  end

  it 'sends a generic error when suggestions generation fails before sending' do
    allow(reply_client).to receive(:generate_reply).and_return(
      conversation_state: 'state-1',
      text: 'reply-1'
    )
    allow(reply_client).to receive(:generate_suggested_replies).and_raise(StandardError, 'slow fail')
    allow(telegram_client).to receive(:with_typing_status).and_yield
    allow(telegram_client).to receive(:send_message)

    expect { handler.handle(update) }.not_to raise_error
    expect(telegram_client).to have_received(:send_message).with('3', TelegramWebhookHandler::GENERIC_ERROR_MESSAGE)
    expect(telegram_client).not_to have_received(:send_message).with('3', 'reply-1', any_args)
  end

  it 'answers callback queries and treats the button text as a new message' do
    allow(reply_client).to receive(:generate_reply).and_return(
      conversation_state: 'state-2',
      text: 'reply-from-button'
    )
    allow(reply_client).to receive(:generate_suggested_replies).and_return([ '再直接啲', '舉個例', '改短啲' ])
    allow(telegram_client).to receive(:answer_callback_query)
    allow(telegram_client).to receive(:clear_message_reply_markup)
    allow(telegram_client).to receive(:with_typing_status).and_yield
    allow(telegram_client).to receive(:send_message)

    handler.handle(callback_update)

    expect(telegram_client).to have_received(:answer_callback_query).with('callback-1')
    expect(telegram_client).to have_received(:clear_message_reply_markup).with('3', 7)
    expect(reply_client).to have_received(:generate_reply).with(
      chat_id: '3',
      text: '再濃縮',
      conversation_state: nil,
      image_file_path: nil,
      memory_context: nil
    )
  end

  it 'resets session and replies with start message for /start' do
    ChatSession.create!(chat_id: '3', last_response_id: 'state-old', updated_at: current_time_ms)
    start_update = update.deep_merge('message' => { 'text' => '/start' })

    allow(telegram_client).to receive(:send_message)

    handler.handle(start_update)

    expect(ChatSession.find_by(chat_id: '3')).to be_nil
    expect(telegram_client).to have_received(:send_message).with(
      '3',
      TelegramWebhookHandler::START_MESSAGE,
      remove_keyboard: true
    )
  end

  it 'replies with new session message and clears suggestion keyboard for /new' do
    new_update = update.deep_merge('message' => { 'text' => '/new' })

    allow(telegram_client).to receive(:send_message)

    handler.handle(new_update)

    expect(telegram_client).to have_received(:send_message).with(
      '3',
      TelegramWebhookHandler::NEW_SESSION_MESSAGE,
      remove_keyboard: true
    )
  end

  it 'shows stored memory for /show_memory' do
    UserMemory.create!(
      telegram_user_id: '234392020',
      kind: 'preference',
      key: 'language',
      value: '廣東話',
      created_at: current_time_ms,
      updated_at: current_time_ms
    )
    allow(telegram_client).to receive(:send_message)

    handler.handle(show_memory_update)

    expect(telegram_client).to have_received(:send_message).with(
      '3',
      "而家記住咗以下資料：\n- [preference] language: 廣東話",
      remove_keyboard: true
    )
  end

  it 'clears stored memory for /clear_memory' do
    UserMemory.create!(
      telegram_user_id: '234392020',
      kind: 'preference',
      key: 'language',
      value: '廣東話',
      created_at: current_time_ms,
      updated_at: current_time_ms
    )
    allow(telegram_client).to receive(:send_message)

    handler.handle(clear_memory_update)

    expect(UserMemory.where(telegram_user_id: '234392020')).to be_empty
    expect(telegram_client).to have_received(:send_message).with(
      '3',
      TelegramWebhookHandler::MEMORY_CLEARED_MESSAGE,
      remove_keyboard: true
    )
  end

  it 'replies unsupported for non-text non-photo messages' do
    unsupported_update = update.deep_merge('message' => { 'text' => nil, 'sticker' => { 'file_id' => 'sticker-1' } })

    allow(reply_client).to receive(:generate_reply)
    allow(telegram_client).to receive(:send_message)

    handler.handle(unsupported_update)

    expect(telegram_client).to have_received(:send_message).with('3', TelegramWebhookHandler::UNSUPPORTED_MESSAGE)
    expect(reply_client).not_to have_received(:generate_reply)
  end

  it 'clears callback keyboard when /start is triggered from an inline button' do
    allow(telegram_client).to receive(:answer_callback_query)
    allow(telegram_client).to receive(:clear_message_reply_markup)
    allow(telegram_client).to receive(:send_message)

    handler.handle(start_callback_update)

    expect(telegram_client).to have_received(:clear_message_reply_markup).with('3', 8)
    expect(telegram_client).to have_received(:send_message).with(
      '3',
      TelegramWebhookHandler::START_MESSAGE,
      remove_keyboard: true
    )
  end

  it 'clears callback keyboard when /new is triggered from an inline button' do
    allow(telegram_client).to receive(:answer_callback_query)
    allow(telegram_client).to receive(:clear_message_reply_markup)
    allow(telegram_client).to receive(:send_message)

    handler.handle(new_callback_update)

    expect(telegram_client).to have_received(:clear_message_reply_markup).with('3', 9)
    expect(telegram_client).to have_received(:send_message).with(
      '3',
      TelegramWebhookHandler::NEW_SESSION_MESSAGE,
      remove_keyboard: true
    )
  end

  def current_time_ms
    (Time.now.to_f * 1000).to_i
  end
end
