require "rails_helper"

RSpec.describe TelegramCommandCatalog do
  describe ".commands" do
    it "returns the supported Telegram command list" do
      expect(described_class.commands).to eq([
        { command: "start", description: "顯示開始使用說明" },
        { command: "new", description: "開一個新 session" },
        { command: "show_memory", description: "顯示你目前嘅記憶" },
        { command: "clear_memory", description: "清除你目前嘅記憶" }
      ])
    end
  end
end
