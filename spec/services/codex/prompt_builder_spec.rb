require "rails_helper"

RSpec.describe Codex::PromptBuilder do
  let(:builder) { described_class.new }
  let(:transcript) do
    Codex::Transcript.from_conversation_state(
      JSON.generate([
        { "role" => "user", "content" => "你好" },
        { "role" => "assistant", "content" => "有咩幫到你" }
      ])
    )
  end

  describe "#build_reply_prompt" do
    it "includes the system prompt, transcript, and structured reply instructions" do
      prompt = builder.build_reply_prompt(transcript, has_image: true, image_count: 1)

      expect(prompt).to include(Conversation::Service::SYSTEM_PROMPT)
      expect(prompt).to include("最新一條用戶訊息有附圖。")
      expect(prompt).to include("1. user: 你好")
      expect(prompt).to include("2. assistant: 有咩幫到你")
      expect(prompt).to include('格式一定要包含 `text` 同 `suggested_replies` 兩個欄位。')
      expect(prompt).to include('格式例子：{"text":"主答案","suggested_replies":["建議回覆 1","建議回覆 2","建議回覆 3"]}。')
      expect(prompt).to include("唔好輸出任何額外文字，唔好用 markdown code fence。")
    end

    it "adds numbered image instructions when multiple images are attached" do
      prompt = builder.build_reply_prompt(transcript, has_image: true, image_count: 3)

      expect(prompt).to include("今次總共有 3 張圖，分析時要用圖 1、圖 2、圖 3 呢類編號逐張講。")
    end

    it "injects long-term memory when present" do
      prompt = builder.build_reply_prompt(
        transcript,
        has_image: false,
        long_term_memory: "- 偏好用廣東話\n- 正在整 Telegram bot"
      )

      expect(prompt).to include("長期記憶：")
      expect(prompt).to include("- 偏好用廣東話")
      expect(prompt).to include("請只喺相關時自然利用以上記憶")
    end
  end
end
