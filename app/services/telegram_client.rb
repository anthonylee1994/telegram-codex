require "cgi"
require "json"
require "net/http"
require "tempfile"
require "tmpdir"
require "uri"

class TelegramClient
  TYPING_INTERVAL_SECONDS = 4
  MAX_SUGGESTED_REPLIES = 3

  def initialize(bot_token: AppConfig.fetch.telegram_bot_token)
    @bot_token = bot_token
  end

  def download_file_to_temp(file_id)
    file = get_file(file_id)
    file_path = file.fetch("file_path")
    temp_dir = Dir.mktmpdir("telegram-codex-file-")
    output_path = File.join(temp_dir, File.basename(file_path))
    response = Net::HTTP.get_response(URI("https://api.telegram.org/file/bot#{@bot_token}/#{file_path}"))
    raise "Failed to download Telegram file: #{response.code} #{response.message}" unless response.is_a?(Net::HTTPSuccess)

    File.binwrite(output_path, response.body)
    output_path
  end

  def send_message(chat_id, text, suggested_replies: [], remove_keyboard: false)
    params = {
      chat_id: chat_id,
      text: format_telegram_message(text),
      parse_mode: "HTML"
    }
    reply_markup = build_reply_markup(suggested_replies, remove_keyboard: remove_keyboard)
    params[:reply_markup] = JSON.generate(reply_markup) if reply_markup.present?
    post_form("sendMessage", params)
  end

  def send_chat_action(chat_id, action)
    post_form("sendChatAction", chat_id: chat_id, action: action)
  end

  def answer_callback_query(callback_query_id)
    post_form("answerCallbackQuery", callback_query_id: callback_query_id)
  end

  def edit_message_reply_markup(chat_id, message_id, suggested_replies: [])
    params = {
      chat_id: chat_id,
      message_id: message_id
    }
    reply_markup = build_reply_markup(suggested_replies)
    params[:reply_markup] = JSON.generate(reply_markup) if reply_markup.present?
    post_form("editMessageReplyMarkup", params)
  end

  def clear_message_reply_markup(chat_id, message_id)
    post_form(
      "editMessageReplyMarkup",
      chat_id: chat_id,
      message_id: message_id,
      reply_markup: JSON.generate(inline_keyboard: [])
    )
  end

  def with_typing_status(chat_id)
    begin
      send_chat_action(chat_id, "typing")
    rescue StandardError
      nil
    end

    stop = false
    thread = Thread.new do
      sleep(TYPING_INTERVAL_SECONDS)

      until stop
        begin
          send_chat_action(chat_id, "typing")
        rescue StandardError
          nil
        end
        sleep(TYPING_INTERVAL_SECONDS)
      end
    end

    yield
  ensure
    stop = true
    thread&.join(0.2)
  end

  def set_webhook(url, secret_token)
    post_form(
      "setWebhook",
      url: url,
      secret_token: secret_token,
      allowed_updates: JSON.generate([ "message" ])
    )

    Rails.logger.info("Telegram webhook configured url=#{url}")
  end

  def set_my_commands(commands)
    post_form("setMyCommands", commands: JSON.generate(commands))
  end

  private

  def get_file(file_id)
    response = Net::HTTP.get_response(URI("#{api_base}/getFile?file_id=#{CGI.escape(file_id)}"))
    raise "Telegram getFile failed: #{response.code} #{response.message}" unless response.is_a?(Net::HTTPSuccess)

    payload = JSON.parse(response.body)
    raise "Telegram getFile did not include a file path." unless payload["ok"] && payload.dig("result", "file_path").present?

    payload.fetch("result")
  end

  def post_form(method_name, params)
    uri = URI("#{api_base}/#{method_name}")
    response = Net::HTTP.post_form(uri, params)
    raise "Telegram #{method_name} failed: #{response.code} #{response.message}" unless response.is_a?(Net::HTTPSuccess)

    payload = JSON.parse(response.body)
    raise "Telegram #{method_name} returned ok=false" unless payload["ok"]

    payload["result"]
  end

  def api_base
    @api_base ||= "https://api.telegram.org/bot#{@bot_token}"
  end

  def format_telegram_message(text)
    placeholders = {}
    formatted = CGI.escapeHTML(text)
    placeholder_index = 0

    formatted = formatted.gsub(/```([\s\S]*?)```/) do
      key = placeholder_key(placeholder_index)
      placeholder_index += 1
      placeholders[key] = "<pre><code>#{::Regexp.last_match(1).strip}</code></pre>"
      key
    end

    formatted = formatted.gsub(/`([^`\n]+)`/, '<code>\\1</code>')
    formatted = formatted.gsub(/\*\*([^*\n]+)\*\*/, '<b>\\1</b>')
    formatted = formatted.gsub(/(^|\n)\#{1,6}\s+([^\n]+)/, '\\1<b>\\2</b>')

    placeholders.each do |key, value|
      formatted = formatted.gsub(key, value)
    end

    formatted
  end

  def placeholder_key(index)
    "TELEGRAM_CODE_BLOCK_#{index}__"
  end

  def build_reply_markup(suggested_replies, remove_keyboard: false)
    return { remove_keyboard: true } if remove_keyboard

    cleaned_replies = Array(suggested_replies).filter_map do |reply|
      next unless reply.is_a?(String)

      normalized_reply = reply.strip
      next if normalized_reply.empty?

      normalized_reply
    end.uniq.first(MAX_SUGGESTED_REPLIES)

    return nil if cleaned_replies.empty?

    {
      keyboard: cleaned_replies.map { |reply| [ { text: reply } ] },
      resize_keyboard: true,
      one_time_keyboard: true
    }
  end
end
