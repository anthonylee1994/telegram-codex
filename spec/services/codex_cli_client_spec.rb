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
  end
end
