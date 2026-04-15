require "rails_helper"

RSpec.describe TelegramClient do
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
