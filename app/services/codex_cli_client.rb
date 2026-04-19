class CodexCliClient
  MAX_SUGGESTED_REPLIES = 3
  DEFAULT_SUGGESTED_REPLIES = [
    "可唔可以講詳細啲？",
    "幫我列重點。",
    "下一步可以點做？"
  ].freeze

  def initialize(reply_parser: CodexReplyParser.new(
    default_suggested_replies: DEFAULT_SUGGESTED_REPLIES,
    max_suggested_replies: MAX_SUGGESTED_REPLIES
  ), exec_runner: CodexExecRunner.new, prompt_builder: CodexPromptBuilder.new)
    @reply_parser = reply_parser
    @exec_runner = exec_runner
    @prompt_builder = prompt_builder
  end

  def generate_reply(chat_id:, text:, conversation_state:, image_file_paths:)
    transcript = CodexTranscript.from_conversation_state(conversation_state)
    user_message = build_user_message(text, image_file_paths)
    next_transcript = transcript.append("user", user_message)
    prompt = @prompt_builder.build_reply_prompt(next_transcript, has_image: image_file_paths.present?)
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

  def build_user_message(text, image_file_paths)
    return text if text.present?
    return "請描述呢啲圖，並按我需要幫我分析。" if Array(image_file_paths).any?

    ""
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
