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
      callback_query_id: nil,
      chat_id: '3',
      image_file_id: nil,
      message_id: 2,
      text: 'hello',
      user_id: '234392020',
      update_id: 1
    )
    expect(parsed.inline_callback?).to eq(false)
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
      callback_query_id: nil,
      chat_id: '3',
      image_file_id: 'large-file',
      message_id: 2,
      text: '睇下呢張圖',
      user_id: '234392020',
      update_id: 1
    )
    expect(parsed.inline_callback?).to eq(false)
  end

  it 'parses callback query from inline keyboard' do
    parsed = parser.parse_incoming_telegram_message(
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
    )

    expect(parsed).to have_attributes(
      callback_query_id: 'callback-1',
      chat_id: '3',
      image_file_id: nil,
      message_id: 7,
      text: '再濃縮',
      user_id: '234392020',
      update_id: 9
    )
    expect(parsed.inline_callback?).to eq(true)
  end
end
