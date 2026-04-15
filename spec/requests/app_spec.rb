require 'rails_helper'

RSpec.describe 'App', type: :request do
  describe 'GET /health' do
    it 'returns ok' do
      get '/health'

      expect(response).to have_http_status(:ok)
      expect(JSON.parse(response.body)).to eq('ok' => true)
    end
  end

  describe 'POST /telegram/webhook' do
    let(:headers) do
      {
        'CONTENT_TYPE' => 'application/json',
        'X-Telegram-Bot-Api-Secret-Token' => secret
      }
    end
    let(:secret) { 'expected-secret' }

    it 'processes webhook with valid secret' do
      handler = instance_double(TelegramWebhookHandler, handle: nil)
      update = {
        update_id: 1,
        message: {
          message_id: 2,
          text: 'hello',
          chat: {
            id: 3
          }
        }
      }

      allow(TelegramWebhookHandlerFactory).to receive(:build).and_return(handler)

      post '/telegram/webhook', params: update.to_json, headers: headers

      expect(response).to have_http_status(:ok)
      expect(JSON.parse(response.body)).to eq('ok' => true)
      expect(handler).to have_received(:handle).with(hash_including('update_id' => 1))
    end

    it 'rejects invalid secret' do
      allow(Rails.logger).to receive(:warn)

      post '/telegram/webhook', params: {}.to_json, headers: headers.merge('X-Telegram-Bot-Api-Secret-Token' => 'wrong-secret')

      expect(response).to have_http_status(:unauthorized)
      expect(JSON.parse(response.body)).to eq('ok' => false)
      expect(Rails.logger).to have_received(:warn)
    end

    it 'returns 500 when handler fails' do
      handler = instance_double(TelegramWebhookHandler)

      allow(TelegramWebhookHandlerFactory).to receive(:build).and_return(handler)
      allow(handler).to receive(:handle).and_raise(StandardError, 'boom')
      allow(Rails.logger).to receive(:error)

      post '/telegram/webhook', params: {}.to_json, headers: headers

      expect(response).to have_http_status(:internal_server_error)
      expect(JSON.parse(response.body)).to eq('ok' => false)
      expect(Rails.logger).to have_received(:error)
    end
  end
end
