require "rails_helper"

RSpec.describe CodexPromptBuilder do
  let(:builder) { described_class.new }
  let(:transcript) do
    CodexTranscript.from_conversation_state(
      JSON.generate([
        { "role" => "user", "content" => "你好" },
        { "role" => "assistant", "content" => "有咩幫到你" }
      ])
    )
  end

  describe "#build_reply_prompt" do
    it "includes the system prompt, transcript, and reply instructions" do
      prompt = builder.build_reply_prompt(transcript, has_image: true)

      expect(prompt).to include(ConversationService::SYSTEM_PROMPT)
      expect(prompt).to include("最新一條用戶訊息有附圖。")
      expect(prompt).to include("1. user: 你好")
      expect(prompt).to include("2. assistant: 有咩幫到你")
      expect(prompt).to include("只輸出助手畀用戶嘅主答案內容。")
    end
  end

  describe "#build_suggested_replies_prompt" do
    it "includes suggested reply instructions" do
      prompt = builder.build_suggested_replies_prompt(transcript)

      expect(prompt).to include("以下係最新對話紀錄，最後一條 assistant 訊息就係啱啱已經發咗畀用戶嘅主答案。")
      expect(prompt).to include('格式一定要係：["建議回覆 1","建議回覆 2","建議回覆 3"]。')
      expect(prompt).to include("唔好輸出任何額外文字，唔好用 markdown code fence。")
    end
  end
end
