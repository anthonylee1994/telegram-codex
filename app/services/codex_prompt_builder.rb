class CodexPromptBuilder
  REPLY_PROMPT_INSTRUCTIONS = [
    "只可以輸出一個 JSON object。",
    "格式一定要包含 `text` 同 `suggested_replies` 兩個欄位。",
    "格式例子：{\"text\":\"主答案\",\"suggested_replies\":[\"建議回覆 1\",\"建議回覆 2\",\"建議回覆 3\"]}。",
    "除非用戶明確要求其他語言，否則一律用廣東話。",
    "text 只可以係助手畀用戶嘅主答案內容。",
    "每個建議回覆都要係用戶下一步可以直接撳嘅簡短廣東話跟進句子。",
    "建議回覆必須係純文字、實用、唔可以留空，而且最多 20 個中文字。",
    "一定要回傳啱啱好 3 個建議回覆。",
    "唔好輸出任何額外文字，唔好用 markdown code fence。"
  ].freeze

  def build_reply_prompt(transcript, has_image:, image_count: 0)
    prefix_sections = []
    prefix_sections << "最新一條用戶訊息有附圖。" if has_image
    prefix_sections << "今次總共有 #{image_count} 張圖，分析時要用圖 1、圖 2、圖 3 呢類編號逐張講。" if image_count > 1

    build_prompt(transcript, REPLY_PROMPT_INSTRUCTIONS, prefix_sections: prefix_sections)
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
