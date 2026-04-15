require "json"

class UserMemoryExtractor
  MEMORY_EXTRACTION_PROMPT = <<~PROMPT.freeze
    你而家負責幫 Telegram 助手抽取「值得長期記住」嘅用戶記憶。

    規則：
    1. 只抽取對之後對話有幫助、而且相對穩定或者值得保留嘅資料。
    2. 如果用戶明確叫你「記住／記低／記得」某項資料，通常應該保存，除非內容太含糊或者無法重用。
    3. 某啲未必永久穩定、但喺之後對話高度可能再用到嘅重要資料都應該保存，例如位置、聯絡資料、預約、安排、偏好、身份背景、工作背景、關係狀態、健康限制、項目背景，或者任何用戶特登提醒你之後要記得嘅資訊。
    4. 唔好只限某一類 domain；旅行、工作、生活、健康、學習、行政安排都可以。
    5. 唔好抽取無實際價值、太私密而且冇必要、或者太含糊嘅內容。
    6. 如果冇任何值得保存嘅記憶，就回傳空陣列。
    7. 只可以輸出嚴格 JSON array，格式：
       [{"kind":"preference","key":"language","value":"廣東話"}]
    8. 每個 object 只可以有 kind、key、value 三個字串欄位。
    9. kind 用簡短英文分類，例如 profile、preference、project、context、travel、work、health。
    10. key 用簡短英文 snake_case。
    11. value 用簡潔自然語言，保留原意，唔好作嘢。
    12. 例子：
        如果用戶話「記低我住緊嘅酒店係 XXX」，合理輸出例如：
        [{"kind":"travel","key":"hotel_name","value":"XXX"}]
        如果用戶話「記住我已經離婚」，合理輸出例如：
        [{"kind":"profile","key":"marital_status","value":"divorced"}]
        如果用戶話「記低我對花生過敏」，合理輸出例如：
        [{"kind":"health","key":"allergy","value":"花生過敏"}]
    13. 唔好輸出 markdown、解釋、額外文字。
  PROMPT

  def initialize(exec_runner: CodexExecRunner.new)
    @exec_runner = exec_runner
  end

  def extract(text:, existing_memories: [])
    normalized_text = text.to_s.strip
    return [] if normalized_text.empty?

    raw_reply = @exec_runner.run(prompt: build_prompt(normalized_text, existing_memories))
    sanitize_memories(parse_payload(raw_reply))
  rescue StandardError => e
    Rails.logger.warn("Failed to extract user memory: #{e.message}")
    []
  end

  private

  def build_prompt(text, existing_memories)
    sections = [ MEMORY_EXTRACTION_PROMPT ]
    sections << format_existing_memories(existing_memories) if existing_memories.any?
    sections << "最新用戶訊息："
    sections << text
    sections.join("\n\n")
  end

  def format_existing_memories(existing_memories)
    [
      "目前已存在記憶：",
      *existing_memories.map { |memory| "- #{memory.kind}.#{memory.key} = #{memory.value}" }
    ].join("\n")
  end

  def parse_payload(raw_reply)
    candidate_payloads(raw_reply).each do |candidate|
      payload = JSON.parse(candidate)
      payload = JSON.parse(payload) if payload.is_a?(String)
      return payload if payload.is_a?(Array)
    rescue JSON::ParserError
      next
    end

    []
  end

  def candidate_payloads(raw_reply)
    normalized_reply = raw_reply.to_s.strip
    unwrapped_reply = unwrap_code_fence(normalized_reply)
    extracted_array = extract_json_array(unwrapped_reply)

    [ normalized_reply, unwrapped_reply, extracted_array ].compact.uniq
  end

  def unwrap_code_fence(text)
    text.sub(/\A```(?:json)?\s*/i, "").sub(/\s*```\z/, "").strip
  end

  def extract_json_array(text)
    start_index = text.index("[")
    end_index = text.rindex("]")
    return nil if start_index.nil? || end_index.nil? || end_index <= start_index

    text[start_index..end_index]
  end

  def sanitize_memories(payload)
    payload.filter_map do |memory|
      next unless memory.is_a?(Hash)

      kind = normalize_token(memory["kind"])
      key = normalize_token(memory["key"])
      value = normalize_value(memory["value"])
      next if kind.empty? || key.empty? || value.empty?

      { kind: kind, key: key, value: value }
    end.uniq
  end

  def normalize_token(value)
    value.to_s.strip.downcase.gsub(/[^a-z0-9_]/, "_").gsub(/\A_+|_+\z/, "").slice(0, 40).to_s
  end

  def normalize_value(value)
    value.to_s.strip.gsub(/\s+/, " ").slice(0, 200).to_s
  end
end
