require 'rails_helper'

RSpec.describe Telegram::WebhookHandler do
  let(:reply_client) { instance_double(Codex::CliClient) }
  let(:telegram_client) { instance_double(Telegram::Client, download_file_to_temp: nil) }
  let(:session_summary_job_class) { class_double(SessionSummaryJob, perform_later: true) }
  let(:config) { telegram_test_config }
  let(:handler_bundle) do
    build_telegram_webhook_handler(
      reply_client: reply_client,
      telegram_client: telegram_client,
      config: config,
      session_summary_job_class: session_summary_job_class
    )
  end
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
  let(:album_update_1) do
    {
      'update_id' => 21,
      'message' => {
        'from' => {
          'id' => 234_392_020
        },
        'media_group_id' => 'album-1',
        'message_id' => 22,
        'caption' => '幫我比較下',
        'photo' => [
          {
            'file_id' => 'album-1-small',
            'file_size' => 100
          },
          {
            'file_id' => 'album-1-large',
            'file_size' => 200
          }
        ],
        'chat' => {
          'id' => 3
        }
      }
    }
  end
  let(:album_update_2) do
    {
      'update_id' => 22,
      'message' => {
        'from' => {
          'id' => 234_392_020
        },
        'media_group_id' => 'album-1',
        'message_id' => 23,
        'photo' => [
          {
            'file_id' => 'album-2-small',
            'file_size' => 100
          },
          {
            'file_id' => 'album-2-large',
            'file_size' => 300
          }
        ],
        'chat' => {
          'id' => 3
        }
      }
    }
  end
  let(:album_update_3) do
    {
      'update_id' => 23,
      'message' => {
        'from' => {
          'id' => 234_392_020
        },
        'media_group_id' => 'album-1',
        'message_id' => 24,
        'photo' => [
          {
            'file_id' => 'album-3-small',
            'file_size' => 100
          },
          {
            'file_id' => 'album-3-large',
            'file_size' => 400
          }
        ],
        'chat' => {
          'id' => 3
        }
      }
    }
  end
  let(:document_image_update) do
    {
      'update_id' => 31,
      'message' => {
        'from' => {
          'id' => 234_392_020
        },
        'message_id' => 32,
        'caption' => '呢張 screenshot 幫我睇',
        'document' => {
          'file_id' => 'document-image-file',
          'file_name' => 'screenshot.png',
          'mime_type' => 'image/png'
        },
        'chat' => {
          'id' => 3
        }
      }
    }
  end
  let(:pdf_document_update) do
    {
      'update_id' => 41,
      'message' => {
        'from' => {
          'id' => 234_392_020
        },
        'message_id' => 42,
        'caption' => '幫我睇呢份 PDF',
        'document' => {
          'file_id' => 'document-pdf-file',
          'file_name' => 'report.pdf',
          'mime_type' => 'application/pdf'
        },
        'chat' => {
          'id' => 3
        }
      }
    }
  end
  it 'ignores an update that was already marked as sent' do
    conversation_service.mark_processed(1, '3', 2)

    allow(telegram_client).to receive(:send_message)

    handler.handle(update)

    expect(telegram_client).not_to have_received(:send_message)
    expect(enqueued_jobs).to be_empty
  end

  it 'enqueues reply generation and marks the update as inflight' do
    handler.handle(update)

    expect(ReplyGenerationJob).to have_been_enqueued.with(
      hash_including(
        "chat_id" => "3",
        "message_id" => 2,
        "text" => "hello",
        "update_id" => 1
      )
    )
    expect(ProcessedUpdate.find_by(update_id: 1)&.sent_at).to be_nil
  end

  it 'does not enqueue twice when the same update re-enters while still inflight' do
    handler.handle(update)
    handler.handle(update)

    expect(ReplyGenerationJob).to have_been_enqueued.exactly(:once)
  end

  it 'clears the inflight claim if enqueueing the job fails' do
    job_class = class_double(ReplyGenerationJob)
    allow(job_class).to receive(:perform_later).and_raise(StandardError, "queue down")
    handler_bundle = build_telegram_webhook_handler(
      reply_client: reply_client,
      telegram_client: telegram_client,
      config: config,
      reply_generation_job_class: job_class
    )

    expect { handler_bundle.first.handle(update) }.to raise_error(StandardError, "queue down")
    expect(ProcessedUpdate.find_by(update_id: 1)).to be_nil
  end

  it 'aggregates album updates into one multi-image job' do
    handler.handle(album_update_1)
    handler.handle(album_update_2)
    sleep(0.08)

    expect(ReplyGenerationJob).to have_been_enqueued.with(
      hash_including(
        "chat_id" => "3",
        "image_file_ids" => ["album-1-large", "album-2-large"],
        "message_id" => 22,
        "text" => "幫我比較下",
        "update_id" => 21
      )
    )
    expect(ProcessedUpdate.find_by(update_id: 21)&.sent_at).to be_nil
    expect(ProcessedUpdate.find_by(update_id: 22)&.sent_at).to be_nil
  end

  it 'treats Telegram image documents as image analysis input' do
    handler.handle(document_image_update)

    expect(ReplyGenerationJob).to have_been_enqueued.with(
      hash_including(
        "chat_id" => "3",
        "image_file_ids" => ["document-image-file"],
        "message_id" => 32,
        "text" => "呢張 screenshot 幫我睇",
        "update_id" => 31
      )
    )
  end

  it 'enqueues Telegram pdf documents for pdf-to-image preprocessing' do
    handler.handle(pdf_document_update)

    expect(ReplyGenerationJob).to have_been_enqueued.with(
      hash_including(
        "chat_id" => "3",
        "image_file_ids" => [],
        "message_id" => 42,
        "pdf_file_id" => "document-pdf-file",
        "text" => "幫我睇呢份 PDF",
        "update_id" => 41
      )
    )
  end

  it 'asks the user to narrow focus when an album contains too many images' do
    tight_config = telegram_test_config.tap { |config| config.max_media_group_images = 2 }
    tight_handler = build_telegram_webhook_handler(
      reply_client: reply_client,
      telegram_client: telegram_client,
      config: tight_config
    ).first
    allow(telegram_client).to receive(:send_message)

    tight_handler.handle(album_update_1)
    tight_handler.handle(album_update_2)
    tight_handler.handle(album_update_3)
    sleep(0.08)

    expect(telegram_client).to have_received(:send_message).with("3", Telegram::WebhookHandler::TOO_MANY_IMAGES_MESSAGE)
    expect(enqueued_jobs).to be_empty
    expect(ProcessedUpdate.find_by(update_id: 21)&.sent_at).to be_present
    expect(ProcessedUpdate.find_by(update_id: 22)&.sent_at).to be_present
    expect(ProcessedUpdate.find_by(update_id: 23)&.sent_at).to be_present
  end

  it 'resets session and replies with start message for /start' do
    ChatSession.create!(chat_id: '3', last_response_id: 'state-old', updated_at: current_time_ms)
    start_update = update.deep_merge('message' => { 'text' => '/start' })

    allow(telegram_client).to receive(:send_message)

    handler.handle(start_update)

    expect(ChatSession.find_by(chat_id: '3')).to be_nil
    expect(telegram_client).to have_received(:send_message).with(
      '3',
      Telegram::WebhookHandler::START_MESSAGE,
      remove_keyboard: true
    )
  end

  it 'replies with new session message and clears suggestion keyboard for /new' do
    new_update = update.deep_merge('message' => { 'text' => '/new' })

    allow(telegram_client).to receive(:send_message)

    handler.handle(new_update)

    expect(telegram_client).to have_received(:send_message).with(
      '3',
      Telegram::WebhookHandler::NEW_SESSION_MESSAGE,
      remove_keyboard: true
    )
  end

  it 'shows help for /help' do
    help_update = update.deep_merge('message' => { 'text' => '/help' })

    allow(telegram_client).to receive(:send_message)

    handler.handle(help_update)

    expect(telegram_client).to have_received(:send_message).with(
      '3',
      Telegram::WebhookHandler::HELP_MESSAGE,
      remove_keyboard: true
    )
  end

  it 'shows runtime status for /status' do
    status_update = update.deep_merge('message' => { 'text' => '/status' })

    allow(telegram_client).to receive(:send_message)

    handler.handle(status_update)

    expect(telegram_client).to have_received(:send_message).with(
      '3',
      include('Bot 狀態：OK'),
      remove_keyboard: true
    )
  end

  it 'shows session metadata for /session' do
    ChatSession.create!(
      chat_id: '3',
      last_response_id: JSON.generate([
        { role: 'user', content: '你好' },
        { role: 'assistant', content: '你好' },
        { role: 'user', content: '再講多句' }
      ]),
      updated_at: current_time_ms
    )
    session_update = update.deep_merge('message' => { 'text' => '/session' })

    allow(telegram_client).to receive(:send_message)

    handler.handle(session_update)

    expect(telegram_client).to have_received(:send_message).with(
      '3',
      include('目前 session：已生效'),
      remove_keyboard: true
    )
  end

  it 'enqueues async summary generation for /summary' do
    summary_update = update.deep_merge('message' => { 'text' => '/summary' })

    allow(telegram_client).to receive(:send_message)

    handler.handle(summary_update)

    expect(session_summary_job_class).to have_received(:perform_later).with('3')
    expect(telegram_client).to have_received(:send_message).with(
      '3',
      Telegram::WebhookHandler::SUMMARY_QUEUED_MESSAGE,
      remove_keyboard: true
    )
  end

  it 'replies unsupported for non-text non-photo messages' do
    unsupported_update = update.deep_merge('message' => { 'text' => nil, 'sticker' => { 'file_id' => 'sticker-1' } })

    allow(telegram_client).to receive(:send_message)

    handler.handle(unsupported_update)

    expect(telegram_client).to have_received(:send_message).with('3', Telegram::WebhookHandler::UNSUPPORTED_MESSAGE)
    expect(enqueued_jobs).to be_empty
  end

  def current_time_ms
    (Time.now.to_f * 1000).to_i
  end
end
