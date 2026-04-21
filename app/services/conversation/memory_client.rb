class Conversation::MemoryClient
  MEMORY_OUTPUT_SCHEMA = {
    type: "object",
    additionalProperties: false,
    required: ["memory"],
    properties: {
      memory: {
        type: "string"
      }
    }
  }.freeze

  def initialize(exec_runner: Codex::ExecRunner.new)
    @exec_runner = exec_runner
  end

  def merge(existing_memory:, user_message:, assistant_reply:)
    raw_reply = @exec_runner.run(
      prompt: build_prompt(existing_memory: existing_memory, user_message: user_message, assistant_reply: assistant_reply),
      output_schema: MEMORY_OUTPUT_SCHEMA
    )
    payload = JSON.parse(raw_reply)

    payload.fetch("memory").to_s.strip
  rescue JSON::ParserError => e
    raise Codex::ExecRunner::ExecutionError, "memory merge returned invalid JSON: #{e.message}"
  end

  private

  def build_prompt(existing_memory:, user_message:, assistant_reply:)
    [
      "你而家負責維護一份 Telegram 用戶嘅長期記憶。",
      "只可以輸出一個 JSON object，格式一定要係 {\"memory\":\"...\"}。",
      "memory 只可以記錄長期有用、同用戶本人有關、之後值得帶入新對話嘅資訊。",
      "可以保留：長期偏好、身份背景、持續目標、固定限制、慣用語言。",
      "唔好保留：一次性任務、短期上下文、臨時問題、敏感憑證、原文長段摘錄、對助手本身嘅要求。",
      "如果新訊息修正咗舊資料，要用新資料覆蓋舊資料。",
      "如果冇任何值得保留嘅內容，而現有記憶亦唔需要改，就原樣輸出現有記憶。",
      "如果所有記憶都應該刪除，就輸出空字串。",
      "記憶內容要簡潔，最好用 1 至 5 行 bullet points，每行一點，用廣東話。",
      "",
      "現有記憶：",
      existing_memory.presence || "（冇）",
      "",
      "最新用戶訊息：",
      user_message.presence || "（冇）",
      "",
      "助手回覆：",
      assistant_reply.presence || "（冇）"
    ].join("\n")
  end
end
