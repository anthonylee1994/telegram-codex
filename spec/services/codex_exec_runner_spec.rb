require "rails_helper"
require "timeout"

RSpec.describe CodexExecRunner do
  let(:runner) { described_class.new(timeout_seconds: 5) }
  let(:status) { instance_double(Process::Status, success?: success) }
  let(:success) { true }

  describe "#run" do
    it "runs codex exec and returns the output file contents" do
      allow(Open3).to receive(:popen3) do |*command, chdir:, &block|
        expect(command).to include("codex", "exec", "--skip-git-repo-check", "--color", "never")
        expect(chdir).to eq(Rails.root.to_s)
        output_path = command[command.index("--output-last-message") + 1]
        File.write(output_path, "reply text\n")
        stdin = StringIO.new
        stdout = StringIO.new("")
        stderr = StringIO.new("")
        wait_thread = instance_double(Process::Waiter, value: status, pid: 123)

        block.call(stdin, stdout, stderr, wait_thread)
      end

      expect(runner.run(prompt: "prompt text", image_file_paths: [])).to eq("reply text")
    end

    it "includes the output schema flag when a schema is provided" do
      allow(Open3).to receive(:popen3) do |*command, chdir:, &block|
        expect(command).to include("--output-schema")
        schema_path = command[command.index("--output-schema") + 1]
        expect(JSON.parse(File.read(schema_path))).to eq({ "type" => "object" })
        output_path = command[command.index("--output-last-message") + 1]
        File.write(output_path, "reply text")
        block.call(StringIO.new, StringIO.new(""), StringIO.new(""), instance_double(Process::Waiter, value: status, pid: 123))
      end

      runner.run(prompt: "prompt text", image_file_paths: [], output_schema: { type: "object" })
    end

    it "includes the image flags when image paths are provided" do
      allow(Open3).to receive(:popen3) do |*command, chdir:, &block|
        expect(command).to include("--image", "/tmp/test-image-a.png")
        expect(command).to include("--image", "/tmp/test-image-b.png")
        output_path = command[command.index("--output-last-message") + 1]
        File.write(output_path, "reply text")
        block.call(StringIO.new, StringIO.new(""), StringIO.new(""), instance_double(Process::Waiter, value: status, pid: 123))
      end

      runner.run(prompt: "prompt text", image_file_paths: ["/tmp/test-image-a.png", "/tmp/test-image-b.png"])
    end

    it "raises when codex exec fails" do
      allow(Open3).to receive(:popen3) do |&block|
        block.call(
          StringIO.new,
          StringIO.new(""),
          StringIO.new("boom"),
          instance_double(Process::Waiter, value: instance_double(Process::Status, success?: false), pid: 123)
        )
      end

      expect {
        runner.run(prompt: "prompt text", image_file_paths: [])
      }.to raise_error(CodexExecRunner::ExecutionError, "codex exec failed: boom")
    end

    it "kills codex exec and raises a timeout error when execution takes too long" do
      runner = described_class.new(timeout_seconds: 1)
      wait_thread = instance_double(Process::Waiter, pid: 456)

      allow(wait_thread).to receive(:value).and_raise(Timeout::Error)
      allow(Open3).to receive(:popen3) do |&block|
        block.call(StringIO.new, StringIO.new(""), StringIO.new(""), wait_thread)
      end
      allow(Process).to receive(:kill)
      allow(Process).to receive(:wait)

      expect {
        runner.run(prompt: "prompt text", image_file_paths: [])
      }.to raise_error(CodexExecRunner::ExecutionTimeoutError, "codex exec timed out after 1 seconds")

      expect(Process).to have_received(:kill).with("TERM", 456)
    end
  end
end
