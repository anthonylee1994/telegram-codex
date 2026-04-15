require "rails_helper"

RSpec.describe CodexReplyParser do
  let(:parser) do
    described_class.new(
      default_suggested_replies: CodexCliClient::DEFAULT_SUGGESTED_REPLIES,
      max_suggested_replies: CodexCliClient::MAX_SUGGESTED_REPLIES
    )
  end

  describe "#parse_reply_text" do
    it "returns plain assistant text directly" do
      expect(parser.parse_reply_text("第一段\n\n第二段")).to eq("第一段\n\n第二段")
    end

    it "extracts text from json replies when the model still outputs json" do
      raw_reply = '{"text":"第一段\\n第二段","suggested_replies":["講多少少","列個重點","下一步呢？"]}'

      expect(parser.parse_reply_text(raw_reply)).to eq("第一段\n第二段")
    end

    it "falls back to the longest string field when text is missing" do
      raw_reply = '{"Bug Report：溝通方式問題":"第一點\\n第二點","suggested_replies":["幫我再縮短","整溫和版","整強硬版"]}'

      expect(parser.parse_reply_text(raw_reply)).to eq("第一點\n第二點")
    end
  end

  describe "#parse_suggested_replies" do
    it "parses a strict json array" do
      expect(parser.parse_suggested_replies('["再濃縮","再直接啲","加例子"]')).to eq([ "再濃縮", "再直接啲", "加例子" ])
    end

    it "extracts suggested replies from json objects or fenced output" do
      raw_reply = <<~TEXT
        ```json
        {"suggested_replies":["幫我再縮短","整溫和版","整強硬版"]}
        ```
      TEXT

      expect(parser.parse_suggested_replies(raw_reply)).to eq([ "幫我再縮短", "整溫和版", "整強硬版" ])
    end

    it "falls back to defaults when parsing fails" do
      expect(parser.parse_suggested_replies("boom")).to eq(CodexCliClient::DEFAULT_SUGGESTED_REPLIES)
    end
  end
end
