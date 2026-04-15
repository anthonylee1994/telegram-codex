class TelegramWebhookHandler
  GENERIC_ERROR_MESSAGE = "我要休息一陣，遲啲叫醒我。"
  NEW_SESSION_MESSAGE = "已經開咗個新 session，你可以重新開始。"
  RATE_LIMIT_MESSAGE = "你打得太快，等一陣再試。"
  START_MESSAGE = [
    "您好，我係您嘅 AI 助手。",
    "",
    "直接 send 文字或者圖片畀我就得。",
    "想重新開過個 session，就打 `/new`。"
  ].join("\n").freeze
  UNAUTHORIZED_MESSAGE = "呢個 bot 暫時只限指定用戶使用。"
  UNSUPPORTED_MESSAGE = "而家只支援文字同圖片訊息，仲未支援檔案、語音。"

  def initialize(
    telegram_update_parser: TelegramUpdateParser.new,
    decision_resolver:,
    action_executor:
  )
    @telegram_update_parser = telegram_update_parser
    @decision_resolver = decision_resolver
    @action_executor = action_executor
  end

  def handle(update)
    message = @telegram_update_parser.parse_incoming_telegram_message(update)
    decision = @decision_resolver.call(message)
    @action_executor.call(decision, update: update)
  end
end
