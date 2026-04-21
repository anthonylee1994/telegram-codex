
namespace :telegram do
  COMMANDS = [
    {
      command: "status",
      description: "Bot 狀態"
    },
    {
      command: "session",
      description: "目前 session 狀態"
    },
    {
      command: "memory",
      description: "長期記憶狀態"
    },
    {
      command: "forget",
      description: "清除長期記憶"
    },
    {
      command: "summary",
      description: "壓縮目前對話 context"
    },
    {
      command: "new",
      description: "新 session"
    },
    {
      command: "help",
      description: "使用說明"
    }
  ].freeze

  desc "Set Telegram webhook to ${BASE_URL}/telegram/webhook"
  task set_webhook: :environment do
    config = AppConfig.fetch
    url = "#{config.base_url}/telegram/webhook"
    Telegram::Client.new.set_webhook(url, config.telegram_webhook_secret)
  end

  desc "Update Telegram bot commands"
  task update_commands: :environment do
    Telegram::Client.new.set_my_commands(COMMANDS)
  end
end
