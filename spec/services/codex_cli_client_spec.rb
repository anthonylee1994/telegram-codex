require "rails_helper"

RSpec.describe CodexCliClient do
  let(:exec_runner) { instance_double(CodexExecRunner) }
  let(:prompt_builder) { CodexPromptBuilder.new }
  let(:client) { described_class.new(exec_runner: exec_runner, prompt_builder: prompt_builder) }

  describe "#generate_reply" do
    it "returns plain assistant text directly" do
      allow(exec_runner).to receive(:run).and_return("第一段\n\n第二段")

      reply = client.generate_reply(
        chat_id: "chat-1",
        conversation_state: nil,
        image_file_path: nil,
        text: "hello"
      )

      expect(reply.fetch(:text)).to eq("第一段\n\n第二段")
    end

    it "extracts text from json replies when the model still outputs json" do
      allow(exec_runner).to receive(:run).and_return(
        '{"text":"第一段\\n第二段","suggested_replies":["講多少少","列個重點","下一步呢？"]}'
      )

      reply = client.generate_reply(
        chat_id: "chat-1",
        conversation_state: nil,
        image_file_path: nil,
        text: "hello"
      )

      expect(reply.fetch(:text)).to eq("第一段\n第二段")
    end

    it "falls back to the longest string field when text is missing" do
      allow(exec_runner).to receive(:run).and_return(
        '{"Bug Report：溝通方式問題":"第一點\\n第二點","suggested_replies":["幫我再縮短","整溫和版","整強硬版"]}'
      )

      reply = client.generate_reply(
        chat_id: "chat-1",
        conversation_state: nil,
        image_file_path: nil,
        text: "hello"
      )

      expect(reply.fetch(:text)).to eq("第一點\n第二點")
    end
  end

  describe "#generate_suggested_replies" do
    it "parses a strict json array" do
      allow(exec_runner).to receive(:run).and_return(
        '["再濃縮","再直接啲","加例子"]'
      )

      suggested_replies = client.generate_suggested_replies(conversation_state: "[]")
      expect(suggested_replies).to eq([ "再濃縮", "再直接啲", "加例子" ])
    end

    it "extracts suggested replies from json objects or fenced output" do
      allow(exec_runner).to receive(:run).and_return(
        <<~TEXT
          ```json
          {"suggested_replies":["幫我再縮短","整溫和版","整強硬版"]}
          ```
        TEXT
      )

      suggested_replies = client.generate_suggested_replies(conversation_state: "[]")
      expect(suggested_replies).to eq([ "幫我再縮短", "整溫和版", "整強硬版" ])
    end

    it "falls back to defaults when parsing fails" do
      allow(exec_runner).to receive(:run).and_raise(StandardError, "boom")

      suggested_replies = client.generate_suggested_replies(conversation_state: "[]")
      expect(suggested_replies).to eq(described_class::DEFAULT_SUGGESTED_REPLIES)
    end
  end
end
