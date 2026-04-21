require "rails_helper"
require "rake"

RSpec.describe "telegram rake tasks" do
  before(:all) do
    Rake.application = Rake::Application.new
    Rails.application.load_tasks
  end

  after(:all) do
    Rake.application = nil
  end

  before do
    Rake::Task["telegram:update_commands"].reenable
  end

  describe "telegram:update_commands" do
    it "updates Telegram bot commands" do
      telegram_client = instance_double(Telegram::Client, set_my_commands: true)

      allow(Telegram::Client).to receive(:new).and_return(telegram_client)

      Rake::Task["telegram:update_commands"].invoke

      expect(telegram_client).to have_received(:set_my_commands).with(
        [
          { command: "status", description: "Bot 狀態" },
          { command: "session", description: "目前 session 狀態" },
          { command: "memory", description: "長期記憶狀態" },
          { command: "forget", description: "清除長期記憶" },
          { command: "summary", description: "壓縮目前對話 context" },
          { command: "new", description: "新 session" },
          { command: "help", description: "使用說明" }
        ]
      )
    end
  end
end
