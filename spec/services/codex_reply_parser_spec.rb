require "rails_helper"

RSpec.describe CodexReplyParser do
  let(:parser) do
    described_class.new(
      default_suggested_replies: CodexCliClient::DEFAULT_SUGGESTED_REPLIES,
      max_suggested_replies: CodexCliClient::MAX_SUGGESTED_REPLIES
    )
  end

  describe "#parse_reply" do
    it "falls back to plain assistant text and default suggestions when json parsing fails" do
      expect(parser.parse_reply("第一段\n\n第二段")).to eq(
        text: "第一段\n\n第二段",
        suggested_replies: CodexCliClient::DEFAULT_SUGGESTED_REPLIES
      )
    end

    it "extracts text and suggestions from structured json replies" do
      raw_reply = '{"text":"第一段\\n第二段","suggested_replies":["講多少少","列個重點","下一步呢？"]}'

      expect(parser.parse_reply(raw_reply)).to eq(
        text: "第一段\n第二段",
        suggested_replies: [ "講多少少", "列個重點", "下一步呢？" ]
      )
    end

    it "falls back to the longest string field when text is missing" do
      raw_reply = '{"Bug Report：溝通方式問題":"第一點\\n第二點","suggested_replies":["幫我再縮短","整溫和版","整強硬版"]}'

      expect(parser.parse_reply(raw_reply)).to eq(
        text: "第一點\n第二點",
        suggested_replies: [ "幫我再縮短", "整溫和版", "整強硬版" ]
      )
    end
  end
end
