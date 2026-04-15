require "rails_helper"
require "rake"

RSpec.describe "telegram:update_commands" do
  before(:all) do
    Rails.application.load_tasks if Rake::Task.tasks.empty?
  end

  before do
    Rake::Task["telegram:update_commands"].reenable
  end

  it "updates Telegram commands from the catalog" do
    telegram_client = instance_double(TelegramClient)

    allow(TelegramClient).to receive(:new).and_return(telegram_client)
    allow(telegram_client).to receive(:set_my_commands)

    Rake::Task["telegram:update_commands"].invoke

    expect(telegram_client).to have_received(:set_my_commands).with(TelegramCommandCatalog.commands)
  end
end
