require "rails_helper"

RSpec.describe CodexCliClient do
  let(:client) { described_class.new }

  describe "#generate_reply" do
    it "normalizes escaped newlines in json replies" do
      allow(client).to receive(:run_codex_exec).and_return(
        '{"text":"第一段\\n\\n第二段","suggested_replies":["講多少少","列個重點","下一步呢？"]}'
      )

      reply = client.generate_reply(
        chat_id: "chat-1",
        conversation_state: nil,
        image_file_path: nil,
        text: "hello"
      )

      expect(reply.fetch(:text)).to eq("第一段\n\n第二段")
      expect(reply.fetch(:suggested_replies)).to eq([ "講多少少", "列個重點", "下一步呢？" ])
    end

    it "parses double-encoded json replies" do
      allow(client).to receive(:run_codex_exec).and_return(
        '"{\\"text\\":\\"第一段\\\\n第二段\\",\\"suggested_replies\\":[\\"講多少少\\",\\"列個重點\\",\\"下一步呢？\\"]}"'
      )

      reply = client.generate_reply(
        chat_id: "chat-1",
        conversation_state: nil,
        image_file_path: nil,
        text: "hello"
      )

      expect(reply.fetch(:text)).to eq("第一段\n第二段")
      expect(reply.fetch(:suggested_replies)).to eq([ "講多少少", "列個重點", "下一步呢？" ])
    end

    it "extracts json replies wrapped in code fences" do
      allow(client).to receive(:run_codex_exec).and_return(
        <<~TEXT
          ```json
          {"text":"整理好咗","suggested_replies":["再短啲","溫和版","可直接send"]}
          ```
        TEXT
      )

      reply = client.generate_reply(
        chat_id: "chat-1",
        conversation_state: nil,
        image_file_path: nil,
        text: "hello"
      )

      expect(reply.fetch(:text)).to eq("整理好咗")
      expect(reply.fetch(:suggested_replies)).to eq([ "再短啲", "溫和版", "可直接send" ])
    end

    it "extracts json replies even with surrounding text" do
      allow(client).to receive(:run_codex_exec).and_return(
        %(以下係結果：\n{"text":"幫你整好","suggested_replies":["再濃縮","再直接啲","加例子"]}\n你睇下。)
      )

      reply = client.generate_reply(
        chat_id: "chat-1",
        conversation_state: nil,
        image_file_path: nil,
        text: "hello"
      )

      expect(reply.fetch(:text)).to eq("幫你整好")
      expect(reply.fetch(:suggested_replies)).to eq([ "再濃縮", "再直接啲", "加例子" ])
    end

    it "falls back to the longest string field when text is missing" do
      allow(client).to receive(:run_codex_exec).and_return(
        '{"Bug Report：溝通方式問題":"第一點\\n第二點","suggested_replies":["幫我再縮短","整溫和版","整強硬版"]}'
      )

      reply = client.generate_reply(
        chat_id: "chat-1",
        conversation_state: nil,
        image_file_path: nil,
        text: "hello"
      )

      expect(reply.fetch(:text)).to eq("第一點\n第二點")
      expect(reply.fetch(:suggested_replies)).to eq([ "幫我再縮短", "整溫和版", "整強硬版" ])
    end
  end
end
