class CodexPromptBuilder
  REPLY_PROMPT_INSTRUCTIONS = [
    "只輸出助手畀用戶嘅主答案內容。",
    "除非用戶明確要求其他語言，否則一律用廣東話。",
    "唔好輸出 JSON。"
  ].freeze
  SUGGESTED_REPLIES_PROMPT_INSTRUCTIONS = [
    "以下係最新對話紀錄，最後一條 assistant 訊息就係啱啱已經發咗畀用戶嘅主答案。",
    "只可以輸出嚴格 JSON array。",
    '格式一定要係：["建議回覆 1","建議回覆 2","建議回覆 3"]。',
    "每個建議回覆都要係用戶下一步可以直接撳嘅簡短廣東話跟進句子。",
    "建議回覆必須係純文字、實用、唔可以留空，而且最多 20 個中文字。",
    "一定要回傳啱啱好 3 個建議回覆。",
    "唔好輸出任何額外文字，唔好用 markdown code fence。"
  ].freeze

  def build_reply_prompt(transcript, has_image:, memory_context: nil)
    prefix_sections = []
    prefix_sections << "最新一條用戶訊息有附圖。" if has_image
    prefix_sections << memory_context if memory_context.present?

    build_prompt(transcript, REPLY_PROMPT_INSTRUCTIONS, prefix_sections: prefix_sections)
  end

  def build_suggested_replies_prompt(transcript)
    build_prompt(transcript, SUGGESTED_REPLIES_PROMPT_INSTRUCTIONS)
  end

  private

  def build_prompt(transcript, instructions, prefix_sections: [])
    [
      ConversationService::SYSTEM_PROMPT,
      *prefix_sections,
      "對話紀錄：",
      *transcript.to_prompt_lines,
      "",
      *instructions
    ].compact.join("\n")
  end
end
