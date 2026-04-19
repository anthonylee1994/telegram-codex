
namespace :telegram do
  COMMANDS = [
    {
      command: "help",
      description: "指令同支援範圍"
    },
    {
      command: "status",
      description: "Bot 狀態"
    },
    {
      command: "session",
      description: "目前 session 狀態"
    },
    {
      command: "summary",
      description: "壓縮目前對話 context"
    },
    {
      command: "new",
      description: "新 session"
    }
  ].freeze

  desc "Set Telegram webhook to ${BASE_URL}/telegram/webhook"
  task set_webhook: :environment do
    config = AppConfig.fetch
    url = "#{config.base_url}/telegram/webhook"
    TelegramClient.new.set_webhook(url, config.telegram_webhook_secret)
  end

  desc "Update Telegram bot commands"
  task update_commands: :environment do
    TelegramClient.new.set_my_commands(COMMANDS)
  end
end
