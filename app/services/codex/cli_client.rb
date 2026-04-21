class Codex::CliClient
  MAX_SUGGESTED_REPLIES = 3
  DEFAULT_SUGGESTED_REPLIES = %w[可唔可以講詳細啲？ 幫我列重點。 下一步可以點做？].freeze

  def initialize(reply_parser: Codex::ReplyParser.new(
    default_suggested_replies: DEFAULT_SUGGESTED_REPLIES,
    max_suggested_replies: MAX_SUGGESTED_REPLIES
  ), exec_runner: Codex::ExecRunner.new, prompt_builder: Codex::PromptBuilder.new)
    @reply_parser = reply_parser
    @exec_runner = exec_runner
    @prompt_builder = prompt_builder
  end

  def generate_reply(chat_id:, text:, conversation_state:, image_file_paths:, reply_to_text: nil)
    transcript = Codex::Transcript.from_conversation_state(conversation_state)
    user_message = build_user_message(text, image_file_paths, reply_to_text: reply_to_text)
    next_transcript = transcript.append("user", user_message)
    prompt = @prompt_builder.build_reply_prompt(
      next_transcript,
      has_image: image_file_paths.present?,
      image_count: Array(image_file_paths).length
    )
    raw_reply = execute_prompt(prompt, image_file_paths, output_schema: reply_output_schema)
    parsed_reply = @reply_parser.parse_reply(raw_reply)
    reply_text = parsed_reply.fetch(:text)
    updated_transcript = next_transcript.append("assistant", reply_text)

    {
      conversation_state: updated_transcript.to_conversation_state,
      suggested_replies: parsed_reply.fetch(:suggested_replies),
      text: reply_text
    }
  end

  private

  def build_user_message(text, image_file_paths, reply_to_text: nil)
    image_count = Array(image_file_paths).length
    base_text = text
    base_text = build_unprompted_image_message(image_count) if base_text.blank? && image_count.positive?
    return base_text unless reply_to_text.present?

    [
      "你而家係回覆緊之前一則訊息。",
      "被引用訊息：#{reply_to_text}",
      "你今次嘅新訊息：#{base_text.presence || '（冇文字）'}"
    ].join("\n")
  end

  def build_unprompted_image_message(image_count)
    return "我上載咗 1 張圖。請先描述圖 1，再按內容幫我分析重點。" if image_count == 1

    image_labels = (1..image_count).map { |index| "圖 #{index}" }.join("、")
    "我上載咗 #{image_count} 張圖。請按 #{image_labels} 逐張描述，再比較異同同整理重點。"
  end

  def execute_prompt(prompt, image_file_paths = [], output_schema: nil)
    @exec_runner.run(prompt: prompt, image_file_paths: image_file_paths, output_schema: output_schema)
  end

  def reply_output_schema
    {
      type: "object",
      additionalProperties: false,
      required: ["text", "suggested_replies"],
      properties: {
        text: {
          type: "string",
          minLength: 1
        },
        suggested_replies: {
          type: "array",
          minItems: MAX_SUGGESTED_REPLIES,
          maxItems: MAX_SUGGESTED_REPLIES,
          items: {
            type: "string",
            minLength: 1
          }
        }
      }
    }
  end
end
