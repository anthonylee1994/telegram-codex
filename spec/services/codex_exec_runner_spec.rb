require "rails_helper"

RSpec.describe CodexExecRunner do
  let(:runner) { described_class.new }
  let(:status) { instance_double(Process::Status, success?: success) }
  let(:success) { true }

  describe "#run" do
    it "runs codex exec and returns the output file contents" do
      allow(Open3).to receive(:capture3) do |*command, stdin_data:, chdir:|
        expect(command).to include("codex", "exec", "--skip-git-repo-check", "--color", "never")
        expect(stdin_data).to eq("prompt text")
        expect(chdir).to eq(Rails.root.to_s)
        output_path = command[command.index("--output-last-message") + 1]
        File.write(output_path, "reply text\n")
        ["", "", status]
      end

      expect(runner.run(prompt: "prompt text", image_file_paths: [])).to eq("reply text")
    end

    it "includes the output schema flag when a schema is provided" do
      allow(Open3).to receive(:capture3) do |*command, stdin_data:, chdir:|
        expect(command).to include("--output-schema")
        schema_path = command[command.index("--output-schema") + 1]
        expect(JSON.parse(File.read(schema_path))).to eq({ "type" => "object" })
        output_path = command[command.index("--output-last-message") + 1]
        File.write(output_path, "reply text")
        ["", "", status]
      end

      runner.run(prompt: "prompt text", image_file_paths: [], output_schema: { type: "object" })
    end

    it "includes the image flags when image paths are provided" do
      allow(Open3).to receive(:capture3) do |*command, stdin_data:, chdir:|
        expect(command).to include("--image", "/tmp/test-image-a.png")
        expect(command).to include("--image", "/tmp/test-image-b.png")
        output_path = command[command.index("--output-last-message") + 1]
        File.write(output_path, "reply text")
        ["", "", status]
      end

      runner.run(prompt: "prompt text", image_file_paths: ["/tmp/test-image-a.png", "/tmp/test-image-b.png"])
    end

    it "raises when codex exec fails" do
      allow(Open3).to receive(:capture3).and_return(["", "boom", instance_double(Process::Status, success?: false)])

      expect {
        runner.run(prompt: "prompt text", image_file_paths: [])
      }.to raise_error("codex exec failed: boom")
    end
  end
end
