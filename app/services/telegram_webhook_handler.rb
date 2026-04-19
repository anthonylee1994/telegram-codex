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
  TOO_MANY_IMAGES_MESSAGE = "你一次過畀太多圖，我未必可以準確逐張睇。揀最多 6 張最關鍵嘅圖，或者直接講明想我集中比較邊幾張、邊一方面。"
  UNAUTHORIZED_MESSAGE = "呢個 bot 暫時只限指定用戶使用。"
  UNSUPPORTED_MESSAGE = "而家只支援文字同圖片訊息，仲未支援檔案、語音。"

  def initialize(
    telegram_update_parser: TelegramUpdateParser.new,
    media_group_aggregator: MediaGroupAggregator.new,
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
    return if message.equal?(MediaGroupAggregator::DEFERRED)

    process_message(message, update: update)
  end

  private

  def process_message(message, update:)
    decision = @decision_resolver.call(message)
    @action_executor.call(decision, update: update)
  end
end
