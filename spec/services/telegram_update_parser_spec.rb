# frozen_string_literal: true

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

    expect(parsed).to eq(
      chat_id: '3',
      image_file_id: nil,
      message_id: 2,
      text: 'hello',
      user_id: '234392020',
      update_id: 1
    )
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

    expect(parsed).to eq(
      chat_id: '3',
      image_file_id: 'large-file',
      message_id: 2,
      text: '睇下呢張圖',
      user_id: '234392020',
      update_id: 1
    )
  end
end
