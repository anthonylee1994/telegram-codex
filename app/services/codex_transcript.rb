require "json"

class CodexTranscript
  MAX_MESSAGES = 100

  def self.from_conversation_state(conversation_state)
    return new([]) if conversation_state.blank?

    messages = JSON.parse(conversation_state).filter_map do |message|
      next unless message.is_a?(Hash)
      next unless %w[user assistant].include?(message["role"])
      next unless message["content"].is_a?(String) && message["content"].strip != ""

      { "role" => message["role"], "content" => message["content"] }
    end

    new(messages)
  rescue JSON::ParserError
    new([])
  end

  def initialize(messages)
    @messages = trim(messages)
  end

  def append(role, content)
    self.class.new(@messages + [ { "role" => role, "content" => content } ])
  end

  def to_conversation_state
    JSON.generate(@messages)
  end

  def to_prompt_lines
    @messages.each_with_index.map do |message, index|
      "#{index + 1}. #{message.fetch('role')}: #{message.fetch('content')}"
    end
  end

  private

  def trim(messages)
    messages.last(MAX_MESSAGES)
  end
end
