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
      telegram_client = instance_double(TelegramClient, set_my_commands: true)

      allow(TelegramClient).to receive(:new).and_return(telegram_client)

      Rake::Task["telegram:update_commands"].invoke

      expect(telegram_client).to have_received(:set_my_commands).with(
        [
          { command: "new", description: "新 session" }
        ]
      )
    end
  end
end
