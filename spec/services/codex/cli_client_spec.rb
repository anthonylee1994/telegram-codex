require "rails_helper"

RSpec.describe Codex::CliClient do
  let(:exec_runner) { instance_double(Codex::ExecRunner) }
  let(:prompt_builder) { Codex::PromptBuilder.new }
  let(:client) { described_class.new(exec_runner: exec_runner, prompt_builder: prompt_builder) }

  describe "#generate_reply" do
    it "returns plain assistant text directly" do
      allow(exec_runner).to receive(:run).and_return(
        '{"text":"第一段\\n\\n第二段","suggested_replies":["講多少少","列個重點","下一步呢？"]}'
      )

      reply = client.generate_reply(
        chat_id: "chat-1",
        conversation_state: nil,
        image_file_paths: [],
        reply_to_text: nil,
        text: "hello"
      )

      expect(reply).to include(
        text: "第一段\n\n第二段",
        suggested_replies: ["講多少少", "列個重點", "下一步呢？"]
      )
    end

    it "extracts text from json replies when the model still outputs json" do
      allow(exec_runner).to receive(:run).and_return(
        '{"text":"第一段\\n第二段","suggested_replies":["講多少少","列個重點","下一步呢？"]}'
      )

      reply = client.generate_reply(
        chat_id: "chat-1",
        conversation_state: nil,
        image_file_paths: [],
        reply_to_text: nil,
        text: "hello"
      )

      expect(reply).to include(
        text: "第一段\n第二段",
        suggested_replies: ["講多少少", "列個重點", "下一步呢？"]
      )
    end

    it "falls back to the longest string field when text is missing" do
      allow(exec_runner).to receive(:run).and_return(
        '{"Bug Report：溝通方式問題":"第一點\\n第二點","suggested_replies":["幫我再縮短","整溫和版","整強硬版"]}'
      )

      reply = client.generate_reply(
        chat_id: "chat-1",
        conversation_state: nil,
        image_file_paths: [],
        reply_to_text: nil,
        text: "hello"
      )

      expect(reply).to include(
        text: "第一點\n第二點",
        suggested_replies: ["幫我再縮短", "整溫和版", "整強硬版"]
      )
    end
  end

  it "uses a plural image prompt when the request only contains images" do
    allow(exec_runner).to receive(:run).and_return("圖像分析結果")

    client.generate_reply(
      chat_id: "chat-1",
      conversation_state: nil,
      image_file_paths: ["/tmp/a.png", "/tmp/b.png"],
      reply_to_text: nil,
      text: ""
    )

    expect(exec_runner).to have_received(:run).with(
      prompt: include("我上載咗 2 張圖。請按 圖 1、圖 2 逐張描述"),
      image_file_paths: ["/tmp/a.png", "/tmp/b.png"],
      output_schema: kind_of(Hash)
    )
  end

  it "adds per-image numbering instructions for multi-image analysis" do
    allow(exec_runner).to receive(:run).and_return("圖像分析結果")

    client.generate_reply(
      chat_id: "chat-1",
      conversation_state: nil,
      image_file_paths: ["/tmp/a.png", "/tmp/b.png", "/tmp/c.png"],
      reply_to_text: nil,
      text: "幫我比較"
    )

    expect(exec_runner).to have_received(:run).with(
      prompt: include("分析時要用圖 1、圖 2、圖 3 呢類編號逐張講。"),
      image_file_paths: ["/tmp/a.png", "/tmp/b.png", "/tmp/c.png"],
      output_schema: kind_of(Hash)
    )
  end

  it "includes replied message context in the prompt when the user replies to an older message" do
    allow(exec_runner).to receive(:run).and_return("圖像分析結果")

    client.generate_reply(
      chat_id: "chat-1",
      conversation_state: nil,
      image_file_paths: [],
      reply_to_text: "之前建議你先檢查 webhook secret。",
      text: "咁下一步呢？"
    )

    expect(exec_runner).to have_received(:run).with(
      prompt: include("你而家係回覆緊之前一則訊息。", "被引用訊息：之前建議你先檢查 webhook secret。", "你今次嘅新訊息：咁下一步呢？"),
      image_file_paths: [],
      output_schema: kind_of(Hash)
    )
  end
end
