require "open3"
require "json"
require "tmpdir"
require "timeout"

class Codex::ExecRunner
  DEFAULT_SANDBOX_MODE = "danger-full-access"
  ExecutionError = Class.new(StandardError)
  ExecutionTimeoutError = Class.new(ExecutionError)

  def initialize(timeout_seconds: AppConfig.fetch.codex_exec_timeout_seconds)
    @timeout_seconds = timeout_seconds
  end

  def run(prompt:, image_file_paths: [], output_schema: nil)
    Dir.mktmpdir("telegram-codex-") do |dir|
      output_path = File.join(dir, "reply.txt")
      schema_path = build_schema_file(dir, output_schema)
      command = build_command(output_path, image_file_paths, schema_path)

      _stdout, stderr, status = run_command(command, prompt)
      raise ExecutionError, "codex exec failed: #{stderr.strip.presence || 'unknown error'}" unless status.success?

      reply_text = File.read(output_path).strip
      raise ExecutionError, "codex exec returned an empty reply" if reply_text.empty?

      reply_text
    end
  end

  private

  attr_reader :timeout_seconds

  def run_command(command, prompt)
    stdout = +""
    stderr = +""
    status = nil

    Open3.popen3(*command, chdir: Rails.root.to_s) do |stdin, stdout_stream, stderr_stream, wait_thread|
      stdin.write(prompt)
      stdin.close

      stdout_reader = Thread.new { stdout_stream.read.to_s }
      stderr_reader = Thread.new { stderr_stream.read.to_s }

      begin
        status = Timeout.timeout(timeout_seconds) { wait_thread.value }
      rescue Timeout::Error
        terminate_process(wait_thread.pid)
        raise ExecutionTimeoutError, "codex exec timed out after #{timeout_seconds} seconds"
      ensure
        stdout = stdout_reader.value
        stderr = stderr_reader.value
      end
    end

    [stdout, stderr, status]
  rescue Timeout::Error
    raise ExecutionTimeoutError, "codex exec timed out after #{timeout_seconds} seconds"
  end

  def terminate_process(pid)
    Process.kill("TERM", pid)
    Timeout.timeout(2) { Process.wait(pid) }
  rescue Errno::ESRCH, Errno::ECHILD
    nil
  rescue Timeout::Error
    Process.kill("KILL", pid)
  rescue Errno::ESRCH
    nil
  end

  def build_command(output_path, image_file_paths, schema_path)
    sandbox_mode = ENV.fetch("CODEX_SANDBOX_MODE", DEFAULT_SANDBOX_MODE)
    command = [
      "codex",
      "exec",
      "--skip-git-repo-check",
      "--sandbox", sandbox_mode,
      "--color", "never",
      "--output-last-message", output_path
    ]
    command += ["--output-schema", schema_path] if schema_path.present?
    Array(image_file_paths).each do |image_file_path|
      command += ["--image", image_file_path] if image_file_path.present?
    end
    command << "-"
    command
  end

  def build_schema_file(dir, output_schema)
    return nil if output_schema.blank?

    schema_path = File.join(dir, "reply-schema.json")
    File.write(schema_path, JSON.generate(output_schema))
    schema_path
  end
end
