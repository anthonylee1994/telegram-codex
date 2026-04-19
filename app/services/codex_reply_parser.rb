require "json"

class CodexReplyParser
  def initialize(default_suggested_replies:, max_suggested_replies:)
    @default_suggested_replies = default_suggested_replies
    @max_suggested_replies = max_suggested_replies
  end

  def parse_reply(raw_reply)
    payload = parse_reply_payload(raw_reply)
    {
      suggested_replies: extract_suggested_replies(payload, raw_reply),
      text: extract_reply_text(payload, raw_reply)
    }
  rescue JSON::ParserError
    {
      suggested_replies: sanitize_suggested_replies(raw_reply),
      text: fallback_reply_text(raw_reply)
    }
  end

  private

  attr_reader :default_suggested_replies, :max_suggested_replies

  def parse_reply_payload(raw_reply)
    candidate_payloads(raw_reply).each do |candidate|
      return candidate if candidate.is_a?(Hash) || candidate.is_a?(Array)

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
    relaxed_payload = extract_relaxed_payload(extracted_object || unwrapped_reply)

    [normalized_reply, unwrapped_reply, extracted_object, relaxed_payload].compact.uniq
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

  def extract_relaxed_payload(text)
    normalized_text = text.to_s.strip
    return nil if normalized_text.empty?

    extracted_text = extract_relaxed_text(normalized_text)
    extracted_suggested_replies = extract_relaxed_suggested_replies(normalized_text)
    return nil if extracted_text.blank? && extracted_suggested_replies.empty?

    {
      "text" => extracted_text,
      "suggested_replies" => extracted_suggested_replies
    }
  end

  def extract_relaxed_text(text)
    match = text.match(/"text"\s*:\s*"(?<value>[\s\S]*?)"\s*,\s*"suggested_replies"\s*:/)
    return "" unless match

    normalize_reply_text(match[:value].to_s)
  end

  def extract_relaxed_suggested_replies(text)
    match = text.match(/"suggested_replies"\s*:\s*\[(?<value>[\s\S]*?)\]/)
    return [] unless match

    match[:value].scan(/"(?<reply>(?:\\.|[^"\\]|[\r\n])*)"/).flatten.map do |reply|
      normalize_reply_text(reply)
    end
  end

  def extract_reply_text(payload, raw_reply)
    direct_text = payload.is_a?(Hash) ? payload["text"].to_s.strip : ""
    return normalize_reply_text(direct_text) if direct_text.present?

    fallback_text = payload.is_a?(Hash) ? longest_string_value(payload) : ""
    return normalize_reply_text(fallback_text) if fallback_text.present?

    fallback_reply_text(raw_reply)
  end

  def extract_suggested_replies(payload, raw_reply)
    extracted_replies = payload.is_a?(Array) ? payload : payload["suggested_replies"]
    sanitize_suggested_replies(extracted_replies)
  rescue StandardError
    sanitize_suggested_replies(raw_reply)
  end

  def longest_string_value(payload)
    payload.values.filter_map do |value|
      next unless value.is_a?(String)

      normalized_value = value.strip
      next if normalized_value.empty?

      normalized_value
    end.max_by(&:length).to_s.strip
  end

  def fallback_reply_text(raw_reply)
    fallback_text = normalize_reply_text(raw_reply.to_s.strip)
    raise "codex exec returned an empty reply" if fallback_text.empty?

    fallback_text
  end

  def sanitize_suggested_replies(suggested_replies)
    cleaned_replies = Array(suggested_replies).filter_map do |reply|
      next unless reply.is_a?(String)

      normalized_reply = reply.strip.gsub(/\s+/, " ")
      next if normalized_reply.empty?

      normalized_reply.slice(0, 40)
    end.uniq.first(max_suggested_replies)

    return default_suggested_replies if cleaned_replies.size < max_suggested_replies

    cleaned_replies
  end
end
