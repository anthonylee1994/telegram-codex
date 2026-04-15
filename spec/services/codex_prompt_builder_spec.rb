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
    it "includes the system prompt, transcript, memory context, and reply instructions" do
      prompt = builder.build_reply_prompt(
        transcript,
        has_image: true,
        memory_context: "已知用戶記憶（只作背景參考；除非同最新訊息直接相關，否則唔好主動重複。如果同最新訊息有衝突，一律以最新訊息為準）：\npreference: language = 廣東話"
      )

      expect(prompt).to include(ConversationService::SYSTEM_PROMPT)
      expect(prompt).to include("最新一條用戶訊息有附圖。")
      expect(prompt).to include("已知用戶記憶（只作背景參考；除非同最新訊息直接相關，否則唔好主動重複。如果同最新訊息有衝突，一律以最新訊息為準）：\npreference: language = 廣東話")
      expect(prompt).to include("1. user: 你好")
      expect(prompt).to include("2. assistant: 有咩幫到你")
      expect(prompt).to include("只輸出助手畀用戶嘅主答案內容。")
      expect(prompt).to include("優先回應最新一條用戶訊息本身，唔好答去其他舊內容或者背景記憶。")
      expect(prompt).to include("如果用戶喺最新訊息提供新資料、修正資料、或者叫你記住某樣嘢，要直接針對嗰條資料簡短確認。")
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
