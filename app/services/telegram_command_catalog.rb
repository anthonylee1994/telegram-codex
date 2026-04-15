class TelegramCommandCatalog
  COMMANDS = [
    { command: "start", description: "開始" },
    { command: "new", description: "新 session" },
    { command: "show_memory", description: "顯示長期記憶" },
    { command: "clear_memory", description: "清除長期記憶" }
  ].freeze

  def self.commands
    COMMANDS
  end
end
