require "open3"
require "tmpdir"

class CodexExecRunner
  DEFAULT_SANDBOX_MODE = "danger-full-access"

  def run(prompt:, image_file_paths: [])
    Dir.mktmpdir("telegram-codex-") do |dir|
      output_path = File.join(dir, "reply.txt")
      command = build_command(output_path, image_file_paths)

      _stdout, stderr, status = Open3.capture3(*command, stdin_data: prompt, chdir: Rails.root.to_s)
      raise "codex exec failed: #{stderr.strip.presence || 'unknown error'}" unless status.success?

      reply_text = File.read(output_path).strip
      raise "codex exec returned an empty reply" if reply_text.empty?

      reply_text
    end
  end

  private

  def build_command(output_path, image_file_paths)
    sandbox_mode = ENV.fetch("CODEX_SANDBOX_MODE", DEFAULT_SANDBOX_MODE)
    command = [
      "codex",
      "exec",
      "--skip-git-repo-check",
      "--sandbox", sandbox_mode,
      "--color", "never",
      "--output-last-message", output_path
    ]
    Array(image_file_paths).each do |image_file_path|
      command += [ "--image", image_file_path ] if image_file_path.present?
    end
    command << "-"
    command
  end
end
