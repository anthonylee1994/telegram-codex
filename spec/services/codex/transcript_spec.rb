require "rails_helper"

RSpec.describe Codex::Transcript do
  describe ".from_conversation_state" do
    it "parses valid user and assistant messages" do
      transcript = described_class.from_conversation_state(
        JSON.generate([
          { "role" => "system", "content" => "ignore" },
          { "role" => "user", "content" => "hello" },
          { "role" => "assistant", "content" => "world" },
          { "role" => "assistant", "content" => "" }
        ])
      )

      expect(transcript.to_prompt_lines).to eq([
        "1. user: hello",
        "2. assistant: world"
      ])
    end

    it "falls back to an empty transcript on invalid json" do
      transcript = described_class.from_conversation_state("nope")

      expect(transcript.to_prompt_lines).to eq([])
    end
  end

  describe "#append" do
    it "adds messages and serializes back to conversation state" do
      transcript = described_class.from_conversation_state(nil)
        .append("user", "hello")
        .append("assistant", "world")

      expect(JSON.parse(transcript.to_conversation_state)).to eq([
        { "role" => "user", "content" => "hello" },
        { "role" => "assistant", "content" => "world" }
      ])
    end
  end
end
