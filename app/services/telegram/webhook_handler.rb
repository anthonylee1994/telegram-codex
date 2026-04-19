class Telegram::WebhookHandler
  GENERIC_ERROR_MESSAGE = "我要休息一陣，遲啲叫醒我。"
  HELP_MESSAGE = [
    "可用 command：",
    "/help - 顯示指令同支援範圍",
    "/status - 睇 bot 狀態",
    "/session - 睇目前 session 狀態",
    "/summary - 將長對話壓縮成新 context",
    "/new - 開新 session",
    "",
    "我而家支援文字、圖片、多圖、圖片 document、PDF、txt/md/html/json/csv、docx/xlsx。"
  ].join("\n").freeze
  NEW_SESSION_MESSAGE = "已經開咗個新 session，你可以重新開始。"
  RATE_LIMIT_MESSAGE = "你打得太快，等一陣再試。"
  SUMMARY_QUEUED_MESSAGE = "開始整理目前 session。整完之後我會再主動 send 摘要畀你。"
  START_MESSAGE = [
    "您好，我係您嘅 AI 助手。",
    "",
    "直接 send 文字或者圖片畀我就得。",
    "想睇指令就打 `/help`。",
    "想重新開過個 session，就打 `/new`。"
  ].join("\n").freeze
  TOO_MANY_IMAGES_MESSAGE = "你一次過畀太多圖，我未必可以準確逐張睇。揀最多 6 張最關鍵嘅圖，或者直接講明想我集中比較邊幾張、邊一方面。"
  UNAUTHORIZED_MESSAGE = "呢個 bot 暫時只限指定用戶使用。"
  UNSUPPORTED_MESSAGE = "你輸入嘅內容，我仲未識得處理。"

  def initialize(
    telegram_update_parser: Telegram::UpdateParser.new,
    media_group_aggregator: Telegram::MediaGroupAggregator.new,
    decision_resolver:,
    action_executor:
  )
    @telegram_update_parser = telegram_update_parser
    @media_group_aggregator = media_group_aggregator
    @decision_resolver = decision_resolver
    @action_executor = action_executor
  end

  def handle(update)
    message = @telegram_update_parser.parse_incoming_telegram_message(update)
    message = @media_group_aggregator.call(message) do |aggregated_message|
      process_message(aggregated_message, update: update)
    end
    return if message.equal?(Telegram::MediaGroupAggregator::DEFERRED)

    process_message(message, update: update)
  end

  private

  def process_message(message, update:)
    decision = @decision_resolver.call(message)
    @action_executor.call(decision, update: update)
  end
end
