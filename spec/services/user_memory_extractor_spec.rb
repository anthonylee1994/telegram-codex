require "rails_helper"

RSpec.describe UserMemoryExtractor do
  let(:exec_runner) { instance_double(CodexExecRunner) }
  let(:extractor) { described_class.new(exec_runner: exec_runner) }

  describe "#extract" do
    it "returns normalized memories from model output" do
      allow(exec_runner).to receive(:run).and_return(<<~JSON)
        ```json
        [
          {"kind":"Preference","key":"Language","value":"廣東話"},
          {"kind":"profile","key":"display name","value":" Anthony "}
        ]
        ```
      JSON

      result = extractor.extract(text: "之後用廣東話，同埋叫我 Anthony")

      expect(result).to eq([
        { kind: "preference", key: "language", value: "廣東話" },
        { kind: "profile", key: "display_name", value: "Anthony" }
      ])
    end

    it "returns an empty array when the model returns invalid payload" do
      allow(exec_runner).to receive(:run).and_return("唔知")

      expect(extractor.extract(text: "hello")).to eq([])
    end

    it "includes existing memories in the prompt" do
      existing_memory = UserMemory.new(
        telegram_user_id: "234392020",
        kind: "preference",
        key: "language",
        value: "廣東話"
      )
      captured_prompt = nil

      allow(exec_runner).to receive(:run) do |prompt:|
        captured_prompt = prompt
        "[]"
      end

      extractor.extract(text: "我都慣咗用廣東話", existing_memories: [ existing_memory ])

      expect(captured_prompt).to include("目前已存在記憶：")
      expect(captured_prompt).to include("- preference.language = 廣東話")
    end
  end
end
