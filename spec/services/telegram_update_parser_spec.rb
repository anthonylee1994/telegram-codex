require 'rails_helper'

RSpec.describe TelegramUpdateParser do
  subject(:parser) { described_class.new }

  it 'parses sender user id from Telegram message' do
    parsed = parser.parse_incoming_telegram_message(
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
    )

    expect(parsed).to have_attributes(
      chat_id: '3',
      image_file_ids: [],
      media_group_id: nil,
      message_id: 2,
      text: 'hello',
      user_id: '234392020',
      update_id: 1
    )
    expect(parsed.image_file_id).to be_nil
  end

  it 'parses Telegram photo message with caption' do
    parsed = parser.parse_incoming_telegram_message(
      {
        'update_id' => 1,
        'message' => {
          'from' => {
            'id' => 234_392_020
          },
          'message_id' => 2,
          'caption' => '睇下呢張圖',
          'photo' => [
            {
              'file_id' => 'small-file',
              'file_size' => 100
            },
            {
              'file_id' => 'large-file',
              'file_size' => 200
            }
          ],
          'chat' => {
            'id' => 3
          }
        }
      }
    )

    expect(parsed).to have_attributes(
      chat_id: '3',
      image_file_ids: ['large-file'],
      media_group_id: nil,
      message_id: 2,
      text: '睇下呢張圖',
      user_id: '234392020',
      update_id: 1
    )
    expect(parsed.image_file_id).to eq('large-file')
  end

  it 'parses Telegram album message and keeps the media group id' do
    parsed = parser.parse_incoming_telegram_message(
      {
        'update_id' => 11,
        'message' => {
          'from' => {
            'id' => 234_392_020
          },
          'media_group_id' => 'album-1',
          'message_id' => 12,
          'caption' => '一齊睇',
          'photo' => [
            {
              'file_id' => 'album-small',
              'file_size' => 100
            },
            {
              'file_id' => 'album-large',
              'file_size' => 200
            }
          ],
          'chat' => {
            'id' => 3
          }
        }
      }
    )

    expect(parsed).to have_attributes(
      chat_id: '3',
      image_file_ids: ['album-large'],
      media_group_id: 'album-1',
      message_id: 12,
      text: '一齊睇',
      user_id: '234392020',
      update_id: 11
    )
  end

  it 'parses Telegram image document message with caption' do
    parsed = parser.parse_incoming_telegram_message(
      {
        'update_id' => 13,
        'message' => {
          'from' => {
            'id' => 234_392_020
          },
          'message_id' => 14,
          'caption' => '睇下張 scan',
          'document' => {
            'file_id' => 'document-image-file',
            'file_name' => 'scan.png',
            'mime_type' => 'image/png'
          },
          'chat' => {
            'id' => 3
          }
        }
      }
    )

    expect(parsed).to have_attributes(
      chat_id: '3',
      image_file_ids: ['document-image-file'],
      media_group_id: nil,
      message_id: 14,
      text: '睇下張 scan',
      user_id: '234392020',
      update_id: 13
    )
  end

  it 'parses pdf documents and keeps the file id for later rasterization' do
    parsed = parser.parse_incoming_telegram_message(
      {
        'update_id' => 15,
        'message' => {
          'from' => {
            'id' => 234_392_020
          },
          'message_id' => 16,
          'caption' => '呢份 PDF 幫我睇',
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
    )

    expect(parsed).to have_attributes(
      chat_id: '3',
      image_file_ids: [],
      media_group_id: nil,
      message_id: 16,
      pdf_file_id: 'document-pdf-file',
      text: '呢份 PDF 幫我睇',
      user_id: '234392020',
      update_id: 15
    )
  end

  it 'parses text documents and keeps the file id for later extraction' do
    parsed = parser.parse_incoming_telegram_message(
      {
        'update_id' => 17,
        'message' => {
          'from' => {
            'id' => 234_392_020
          },
          'message_id' => 18,
          'caption' => '幫我睇內容',
          'document' => {
            'file_id' => 'document-text-file',
            'file_name' => 'notes.md',
            'mime_type' => 'text/markdown'
          },
          'chat' => {
            'id' => 3
          }
        }
      }
    )

    expect(parsed).to have_attributes(
      chat_id: '3',
      image_file_ids: [],
      media_group_id: nil,
      message_id: 18,
      pdf_file_id: nil,
      text: '幫我睇內容',
      text_document_file_id: 'document-text-file',
      text_document_name: 'notes.md',
      user_id: '234392020',
      update_id: 17
    )
  end

  it 'parses html documents and keeps the file id for later extraction' do
    parsed = parser.parse_incoming_telegram_message(
      {
        'update_id' => 19,
        'message' => {
          'from' => {
            'id' => 234_392_020
          },
          'message_id' => 20,
          'caption' => '幫我睇 HTML',
          'document' => {
            'file_id' => 'document-html-file',
            'file_name' => 'page.html',
            'mime_type' => 'text/html'
          },
          'chat' => {
            'id' => 3
          }
        }
      }
    )

    expect(parsed).to have_attributes(
      chat_id: '3',
      image_file_ids: [],
      media_group_id: nil,
      message_id: 20,
      pdf_file_id: nil,
      text: '幫我睇 HTML',
      text_document_file_id: 'document-html-file',
      text_document_name: 'page.html',
      user_id: '234392020',
      update_id: 19
    )
  end
end
