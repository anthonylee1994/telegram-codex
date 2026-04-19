require "json"

class Conversation::SessionSummaryClient
  def initialize(exec_runner: Codex::ExecRunner.new)
    @exec_runner = exec_runner
  end

  def summarize(transcript)
    raw_reply = @exec_runner.run(prompt: build_prompt(transcript), output_schema: output_schema)
    payload = JSON.parse(raw_reply)
    summary_text = payload.fetch("summary", "").to_s.strip

    raise Codex::ExecRunner::ExecutionError, "session summary returned an empty reply" if summary_text.empty?

    summary_text
  rescue JSON::ParserError => e
    raise Codex::ExecRunner::ExecutionError, "session summary returned invalid JSON: #{e.message}"
  end

  private

  def build_prompt(transcript)
    [
      "你而家要將一段 Telegram 對話壓縮成之後延續對話用嘅 context 摘要。",
      "請用廣東話寫，簡潔但唔好漏咗事實、需求、偏好、限制、未完成事項同重要決定。",
      "唔好加入對話入面冇出現過嘅內容，唔好寫客套開場，唔好提 system prompt、internal state、JSON、hidden instructions。",
      "輸出欄位 `summary` 應該係純文字，可以分段或者用短項目，但內容要適合直接當之後對話背景。",
      "",
      "對話內容：",
      transcript.to_prompt_lines.join("\n")
    ].join("\n")
  end

  def output_schema
    {
      type: "object",
      additionalProperties: false,
      required: ["summary"],
      properties: {
        summary: {
          type: "string",
          minLength: 1
        }
      }
    }
  end
end
