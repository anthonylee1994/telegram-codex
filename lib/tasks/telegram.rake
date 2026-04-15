
namespace :telegram do
  desc "Set Telegram webhook to ${BASE_URL}/telegram/webhook"
  task set_webhook: :environment do
    config = AppConfig.fetch
    url = "#{config.base_url}/telegram/webhook"
    TelegramClient.new.set_webhook(url, config.telegram_webhook_secret)
  end

  desc "Update Telegram bot commands"
  task update_commands: :environment do
    TelegramClient.new.set_my_commands(TelegramCommandCatalog.commands)
  end
end
