require "json"
require "open3"
require "tmpdir"

class CodexCliClient
  MAX_TRANSCRIPT_MESSAGES = 100
  MAX_SUGGESTED_REPLIES = 3
  DEFAULT_SANDBOX_MODE = "danger-full-access"
  DEFAULT_SUGGESTED_REPLIES = [
    "可唔可以講詳細啲？",
    "幫我列重點。",
    "下一步可以點做？"
  ].freeze

  def generate_reply(chat_id:, text:, conversation_state:, image_file_path:)
    transcript = parse_conversation_state(conversation_state)
    user_message = build_user_message(text, image_file_path)
    next_transcript = trim_transcript(transcript + [ { "role" => "user", "content" => user_message } ])
    prompt = build_reply_prompt(next_transcript, image_file_path.present?)
    raw_reply = run_codex_exec(prompt, image_file_path)
    reply_text = parse_reply_text(raw_reply)
    updated_transcript = trim_transcript(next_transcript + [ { "role" => "assistant", "content" => reply_text } ])

    {
      conversation_state: JSON.generate(updated_transcript),
      text: reply_text
    }
  end

  def generate_suggested_replies(conversation_state:)
    transcript = parse_conversation_state(conversation_state)
    prompt = build_suggested_replies_prompt(transcript)
    raw_reply = run_codex_exec(prompt, nil)
    parse_suggested_replies(raw_reply)
  rescue StandardError
    DEFAULT_SUGGESTED_REPLIES
  end

  private

  def parse_conversation_state(conversation_state)
    return [] if conversation_state.blank?

    JSON.parse(conversation_state).filter_map do |message|
      next unless message.is_a?(Hash)
      next unless %w[user assistant].include?(message["role"])
      next unless message["content"].is_a?(String) && message["content"].strip != ""

      { "role" => message["role"], "content" => message["content"] }
    end
  rescue JSON::ParserError
    []
  end

  def trim_transcript(transcript)
    transcript.last(MAX_TRANSCRIPT_MESSAGES)
  end

  def build_user_message(text, image_file_path)
    return text if text.present?
    return "請描述呢張圖，並按我需要幫我分析。" if image_file_path.present?

    ""
  end

  def build_reply_prompt(transcript, has_image)
    lines = transcript.each_with_index.map do |message, index|
      "#{index + 1}. #{message.fetch('role')}: #{message.fetch('content')}"
    end

    [
      ConversationService::SYSTEM_PROMPT,
      ("最新一條用戶訊息有附圖。" if has_image),
      "對話紀錄：",
      *lines,
      "",
      "只輸出助手畀用戶嘅主答案內容。",
      "除非用戶明確要求其他語言，否則一律用廣東話。",
      "唔好輸出 JSON。",
      "唔好輸出 markdown code fence。"
    ].compact.join("\n")
  end

  def build_suggested_replies_prompt(transcript)
    lines = transcript.each_with_index.map do |message, index|
      "#{index + 1}. #{message.fetch('role')}: #{message.fetch('content')}"
    end

    [
      ConversationService::SYSTEM_PROMPT,
      "以下係最新對話紀錄，最後一條 assistant 訊息就係啱啱已經發咗畀用戶嘅主答案。",
      "對話紀錄：",
      *lines,
      "",
      "只可以輸出嚴格 JSON array。",
      '格式一定要係：["建議回覆 1","建議回覆 2","建議回覆 3"]。',
      "每個建議回覆都要係用戶下一步可以直接撳嘅簡短廣東話跟進句子。",
      "建議回覆必須係純文字、實用、唔可以留空，而且最多 20 個中文字。",
      "一定要回傳啱啱好 3 個建議回覆。",
      "唔好輸出任何額外文字，唔好用 markdown code fence。"
    ].compact.join("\n")
  end

  def parse_reply_text(raw_reply)
    payload = parse_reply_payload(raw_reply)
    text = extract_reply_text(payload)
    return normalize_reply_text(text) if text.present?

    text = normalize_reply_text(raw_reply.to_s.strip)
    raise "codex exec returned an empty reply" if text.empty?

    text
  rescue JSON::ParserError
    text = normalize_reply_text(raw_reply.to_s.strip)
    raise "codex exec returned an empty reply" if text.empty?

    text
  end

  def parse_suggested_replies(raw_reply)
    payload = parse_reply_payload(raw_reply)
    extracted_replies = if payload.is_a?(Array)
                          payload
    else
                          payload["suggested_replies"]
    end
    sanitize_suggested_replies(extracted_replies)
  rescue JSON::ParserError
    sanitize_suggested_replies(raw_reply)
  end

  def parse_reply_payload(raw_reply)
    candidate_payloads(raw_reply).each do |candidate|
      payload = parse_json_candidate(candidate)
      return payload if payload.is_a?(Hash) || payload.is_a?(Array)
    rescue JSON::ParserError
      next
    end

    raise JSON::ParserError, "reply payload is not an object or array"
  end

  def normalize_reply_text(text)
    normalized_text = text

    if normalized_text.include?("\\n") || normalized_text.include?("\\r") || normalized_text.include?("\\t")
      normalized_text = normalized_text.gsub("\\r\\n", "\n").gsub("\\n", "\n").gsub("\\r", "\r").gsub("\\t", "\t")
    end

    normalized_text.strip
  end

  def candidate_payloads(raw_reply)
    normalized_reply = raw_reply.to_s.strip
    unwrapped_reply = unwrap_code_fence(normalized_reply)
    extracted_object = extract_json_object(unwrapped_reply)

    [ normalized_reply, unwrapped_reply, extracted_object ].compact.uniq
  end

  def parse_json_candidate(candidate)
    payload = JSON.parse(candidate)
    payload = JSON.parse(payload) if payload.is_a?(String)
    raise JSON::ParserError, "reply payload is not an object or array" unless payload.is_a?(Hash) || payload.is_a?(Array)

    payload
  end

  def unwrap_code_fence(text)
    text.sub(/\A```(?:json)?\s*/i, "").sub(/\s*```\z/, "").strip
  end

  def extract_json_object(text)
    start_index = text.index("{")
    end_index = text.rindex("}")
    return nil if start_index.nil? || end_index.nil? || end_index <= start_index

    text[start_index..end_index]
  end

  def extract_reply_text(payload)
    return "" unless payload.is_a?(Hash)

    direct_text = payload["text"].to_s.strip
    return direct_text if direct_text.present?

    fallback_text = payload.values.filter_map do |value|
      next unless value.is_a?(String)

      normalized_value = value.strip
      next if normalized_value.empty?

      normalized_value
    end.max_by(&:length)

    fallback_text.to_s.strip
  end

  def sanitize_suggested_replies(suggested_replies)
    cleaned_replies = Array(suggested_replies).filter_map do |reply|
      next unless reply.is_a?(String)

      normalized_reply = reply.strip.gsub(/\s+/, " ")
      next if normalized_reply.empty?

      normalized_reply.slice(0, 40)
    end.uniq.first(MAX_SUGGESTED_REPLIES)

    return DEFAULT_SUGGESTED_REPLIES if cleaned_replies.size < MAX_SUGGESTED_REPLIES

    cleaned_replies
  end

  def run_codex_exec(prompt, image_file_path)
    Dir.mktmpdir("telegram-codex-") do |dir|
      output_path = File.join(dir, "reply.txt")
      sandbox_mode = ENV.fetch("CODEX_SANDBOX_MODE", DEFAULT_SANDBOX_MODE)
      command = [
        "codex",
        "exec",
        "--skip-git-repo-check",
        "--sandbox", sandbox_mode,
        "--color", "never",
        "--output-last-message", output_path
      ]
      command += [ "--image", image_file_path ] if image_file_path.present?
      command << "-"

      _stdout, stderr, status = Open3.capture3(*command, stdin_data: prompt, chdir: Rails.root.to_s)
      raise "codex exec failed: #{stderr.strip.presence || 'unknown error'}" unless status.success?

      reply_text = File.read(output_path).strip
      raise "codex exec returned an empty reply" if reply_text.empty?

      reply_text
    end
  end
end
