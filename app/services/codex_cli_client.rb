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
    user_message = if text.present?
                     text
    elsif image_file_path.present?
                     "請描述呢張圖，並按我需要幫我分析。"
    else
                     ""
    end

    next_transcript = trim_transcript(transcript + [ { "role" => "user", "content" => user_message } ])
    prompt = build_prompt(next_transcript, image_file_path.present?)
    raw_reply = run_codex_exec(prompt, image_file_path)
    reply = parse_reply(raw_reply)
    updated_transcript = trim_transcript(next_transcript + [ { "role" => "assistant", "content" => reply.fetch(:text) } ])

    {
      conversation_state: JSON.generate(updated_transcript),
      suggested_replies: reply.fetch(:suggested_replies),
      text: reply.fetch(:text)
    }
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

  def build_prompt(transcript, has_image)
    lines = transcript.each_with_index.map do |message, index|
      "#{index + 1}. #{message.fetch('role')}: #{message.fetch('content')}"
    end

    [
      ConversationService::SYSTEM_PROMPT,
      ("The latest user message includes an attached image." if has_image),
      "Conversation so far:",
      *lines,
      "",
      "Return strict JSON only.",
      'Use this schema: {"text":"assistant reply","suggested_replies":["short reply button 1","short reply button 2","short reply button 3"]}.',
      "The text reply must be in Cantonese unless the user clearly asked for another language.",
      "Each suggested reply must be a short Cantonese follow-up the user can tap next.",
      "Suggested replies must be plain text, practical, non-empty, and at most 20 Chinese characters.",
      "Always return exactly 3 suggested replies.",
      "Do not wrap the JSON in markdown fences."
    ].compact.join("\n")
  end

  def parse_reply(raw_reply)
    payload = parse_reply_payload(raw_reply)
    text = extract_reply_text(payload)
    raise "codex exec returned an empty reply" if text.empty?

    {
      suggested_replies: sanitize_suggested_replies(payload["suggested_replies"]),
      text: normalize_reply_text(text)
    }
  rescue JSON::ParserError
    text = normalize_reply_text(raw_reply.to_s.strip)
    raise "codex exec returned an empty reply" if text.empty?

    {
      suggested_replies: DEFAULT_SUGGESTED_REPLIES,
      text: text
    }
  end

  def parse_reply_payload(raw_reply)
    candidate_payloads(raw_reply).each do |candidate|
      payload = parse_json_candidate(candidate)
      return payload if payload.is_a?(Hash)
    rescue JSON::ParserError
      next
    end

    raise JSON::ParserError, "reply payload is not an object"
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
    raise JSON::ParserError, "reply payload is not an object" unless payload.is_a?(Hash)

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
