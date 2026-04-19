require "rails_helper"

RSpec.describe Telegram::Client do
  describe "#set_my_commands" do
    it "posts commands to Telegram" do
      client = described_class.new(bot_token: "token")
      commands = [
        { command: "new", description: "新 session" }
      ]

      allow(client).to receive(:post_form)

      client.set_my_commands(commands)

      expect(client).to have_received(:post_form).with("setMyCommands", commands: JSON.generate(commands))
    end
  end

  describe "#with_typing_status" do
    it "sends typing immediately before yielding" do
      client = described_class.new(bot_token: "token")
      call_order = []

      allow(client).to receive(:send_chat_action) do
        call_order << :typing
      end

      client.with_typing_status("chat-1") do
        call_order << :yield
      end

      expect(call_order.first(2)).to eq([:typing, :yield])
    end
  end

  describe "#send_message" do
    it "extracts the text field when structured json leaks into the outbound message" do
      client = described_class.new(bot_token: "token")

      allow(client).to receive(:post_form)

      client.send_message(
        "chat-1",
        '{"text":"淨係出呢句","suggested_replies":["一","二","三"]}',
        suggested_replies: ["一", "二", "三"]
      )

      expect(client).to have_received(:post_form).with(
        "sendMessage",
        hash_including(
          chat_id: "chat-1",
          text: "淨係出呢句",
          parse_mode: "HTML",
          reply_markup: JSON.generate(
            keyboard: [
              [{ text: "一" }],
              [{ text: "二" }],
              [{ text: "三" }]
            ],
            resize_keyboard: true,
            one_time_keyboard: true
          )
        )
      )
    end

    it "extracts the text field when pseudo-json with raw newlines leaks into the outbound message" do
      client = described_class.new(bot_token: "token")
      raw_reply = <<~TEXT.strip
        {"text":"簡易版麵包布甸食譜：
        材料：方包 4 片、雞蛋 2 隻、牛奶 200ml。","suggested_replies":["要焗幾耐？","可以少甜啲嗎？","早餐啱唔啱？"]}
      TEXT

      allow(client).to receive(:post_form)

      client.send_message("chat-1", raw_reply)

      expect(client).to have_received(:post_form).with(
        "sendMessage",
        hash_including(
          chat_id: "chat-1",
          text: "簡易版麵包布甸食譜：\n材料：方包 4 片、雞蛋 2 隻、牛奶 200ml。",
          parse_mode: "HTML",
          reply_markup: JSON.generate(
            keyboard: [
              [{ text: "要焗幾耐？" }],
              [{ text: "可以少甜啲嗎？" }],
              [{ text: "早餐啱唔啱？" }]
            ],
            resize_keyboard: true,
            one_time_keyboard: true
          )
        )
      )
    end
  end
end
