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

  def generate_reply(chat_id:, text:, conversation_state:, image_file_path:)
    transcript = CodexTranscript.from_conversation_state(conversation_state)
    user_message = build_user_message(text, image_file_path)
    next_transcript = transcript.append("user", user_message)
    prompt = @prompt_builder.build_reply_prompt(next_transcript, has_image: image_file_path.present?)
    raw_reply = execute_prompt(prompt, image_file_path)
    reply_text = @reply_parser.parse_reply_text(raw_reply)
    updated_transcript = next_transcript.append("assistant", reply_text)

    {
      conversation_state: updated_transcript.to_conversation_state,
      text: reply_text
    }
  end

  def generate_suggested_replies(conversation_state:)
    transcript = CodexTranscript.from_conversation_state(conversation_state)
    prompt = @prompt_builder.build_suggested_replies_prompt(transcript)
    raw_reply = execute_prompt(prompt)
    @reply_parser.parse_suggested_replies(raw_reply)
  rescue StandardError
    DEFAULT_SUGGESTED_REPLIES
  end

  private

  def build_user_message(text, image_file_path)
    return text if text.present?
    return "請描述呢張圖，並按我需要幫我分析。" if image_file_path.present?

    ""
  end

  def execute_prompt(prompt, image_file_path = nil)
    @exec_runner.run(prompt: prompt, image_file_path: image_file_path)
  end
end
