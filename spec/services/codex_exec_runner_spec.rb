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
        [ "", "", status ]
      end

      expect(runner.run(prompt: "prompt text")).to eq("reply text")
    end

    it "includes the image flag when an image path is provided" do
      allow(Open3).to receive(:capture3) do |*command, stdin_data:, chdir:|
        expect(command).to include("--image", "/tmp/test-image.png")
        output_path = command[command.index("--output-last-message") + 1]
        File.write(output_path, "reply text")
        [ "", "", status ]
      end

      runner.run(prompt: "prompt text", image_file_path: "/tmp/test-image.png")
    end

    it "raises when codex exec fails" do
      allow(Open3).to receive(:capture3).and_return([ "", "boom", instance_double(Process::Status, success?: false) ])

      expect {
        runner.run(prompt: "prompt text")
      }.to raise_error("codex exec failed: boom")
    end
  end
end
