require "rails_helper"

RSpec.describe TelegramClient do
  describe "#set_my_commands" do
    it "posts commands to Telegram" do
      client = described_class.new(bot_token: "token")
      commands = [
        { command: "new", description: "新 session" }
      ]

      allow(client).to receive(:post_form)

      client.set_my_commands(commands)

      expect(client).to have_received(:post_form).with("setMyCommands", commands: JSON.generate(commands))
    end
  end

  describe "#with_typing_status" do
    it "sends typing immediately before yielding" do
      client = described_class.new(bot_token: "token")
      call_order = []

      allow(client).to receive(:send_chat_action) do
        call_order << :typing
      end

      client.with_typing_status("chat-1") do
        call_order << :yield
      end

      expect(call_order.first(2)).to eq([ :typing, :yield ])
    end
  end
end
