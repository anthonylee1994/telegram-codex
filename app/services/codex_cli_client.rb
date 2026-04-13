# frozen_string_literal: true

require 'json'
require 'open3'
require 'tmpdir'

class CodexCliClient
  MAX_TRANSCRIPT_MESSAGES = 100

  def generate_reply(chat_id:, text:, conversation_state:, image_file_path:)
    transcript = parse_conversation_state(conversation_state)
    user_message = if text.present?
                     text
                   elsif image_file_path.present?
                     '請描述呢張圖，並按我需要幫我分析。'
                   else
                     ''
                   end

    next_transcript = trim_transcript(transcript + [{ 'role' => 'user', 'content' => user_message }])
    prompt = build_prompt(next_transcript, image_file_path.present?)
    reply_text = run_codex_exec(prompt, image_file_path)
    updated_transcript = trim_transcript(next_transcript + [{ 'role' => 'assistant', 'content' => reply_text }])

    {
      conversation_state: JSON.generate(updated_transcript),
      text: reply_text
    }
  end

  private

  def parse_conversation_state(conversation_state)
    return [] if conversation_state.blank?

    JSON.parse(conversation_state).filter_map do |message|
      next unless message.is_a?(Hash)
      next unless %w[user assistant].include?(message['role'])
      next unless message['content'].is_a?(String) && message['content'].strip != ''

      { 'role' => message['role'], 'content' => message['content'] }
    end
  rescue JSON::ParserError
    []
  end

  def trim_transcript(transcript)
    transcript.last(MAX_TRANSCRIPT_MESSAGES)
  end

  def build_prompt(transcript, has_image)
    lines = transcript.each_with_index.map do |message, index|
      "#{index + 1}. #{message.fetch('role')}: #{message.fetch('content')}"
    end

    [
      ConversationService::SYSTEM_PROMPT,
      ('The latest user message includes an attached image.' if has_image),
      'Conversation so far:',
      *lines,
      '',
      'Reply only with the assistant message for the latest user input.'
    ].compact.join("\n")
  end

  def run_codex_exec(prompt, image_file_path)
    Dir.mktmpdir('telegram-codex-') do |dir|
      output_path = File.join(dir, 'reply.txt')
      command = [
        'codex',
        'exec',
        '--skip-git-repo-check',
        '--dangerously-bypass-approvals-and-sandbox',
        '--color', 'never',
        '--output-last-message', output_path
      ]
      command += ['--image', image_file_path] if image_file_path.present?
      command << '-'

      _stdout, stderr, status = Open3.capture3(*command, stdin_data: prompt, chdir: Rails.root.to_s)
      raise "codex exec failed: #{stderr.strip.presence || 'unknown error'}" unless status.success?

      reply_text = File.read(output_path).strip
      raise 'codex exec returned an empty reply' if reply_text.empty?

      reply_text
    end
  end
end
