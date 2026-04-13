# frozen_string_literal: true

namespace :telegram do
  desc 'Set Telegram webhook to ${BASE_URL}/telegram/webhook'
  task set_webhook: :environment do
    config = AppConfig.fetch
    url = "#{config.base_url}/telegram/webhook"
    TelegramClient.new.set_webhook(url, config.telegram_webhook_secret)
  end
end
