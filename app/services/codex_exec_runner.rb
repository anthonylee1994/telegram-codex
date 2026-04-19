require "open3"
require "json"
require "tmpdir"

class CodexExecRunner
  DEFAULT_SANDBOX_MODE = "danger-full-access"

  def run(prompt:, image_file_paths: [], output_schema: nil)
    Dir.mktmpdir("telegram-codex-") do |dir|
      output_path = File.join(dir, "reply.txt")
      schema_path = build_schema_file(dir, output_schema)
      command = build_command(output_path, image_file_paths, schema_path)

      _stdout, stderr, status = Open3.capture3(*command, stdin_data: prompt, chdir: Rails.root.to_s)
      raise "codex exec failed: #{stderr.strip.presence || 'unknown error'}" unless status.success?

      reply_text = File.read(output_path).strip
      raise "codex exec returned an empty reply" if reply_text.empty?

      reply_text
    end
  end

  private

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
