require "fileutils"
require "uri"

class AppConfig
  Config = Struct.new(
    :allowed_telegram_user_ids,
    :base_url,
    :port,
    :rate_limit_max_messages,
    :rate_limit_window_ms,
    :session_ttl_days,
    :sqlite_db_path,
    :telegram_bot_token,
    :telegram_webhook_secret,
    keyword_init: true
  )

  class << self
    def fetch
      @fetch ||= build
    end

    def reset!
      @fetch = nil
    end

    private

    def build
      sqlite_db_path = File.expand_path(fetch_string("SQLITE_DB_PATH", default: "./data/app.db"), Rails.root.to_s)
      FileUtils.mkdir_p(File.dirname(sqlite_db_path))

      Config.new(
        allowed_telegram_user_ids: allowed_telegram_user_ids,
        base_url: fetch_url("BASE_URL"),
        port: fetch_integer("PORT", default: 3000),
        rate_limit_max_messages: fetch_integer("RATE_LIMIT_MAX_MESSAGES", default: 5),
        rate_limit_window_ms: fetch_integer("RATE_LIMIT_WINDOW_MS", default: 10_000),
        session_ttl_days: fetch_integer("SESSION_TTL_DAYS", default: 7),
        sqlite_db_path: sqlite_db_path,
        telegram_bot_token: fetch_string("TELEGRAM_BOT_TOKEN"),
        telegram_webhook_secret: fetch_string("TELEGRAM_WEBHOOK_SECRET")
      )
    end

    def allowed_telegram_user_ids
      ENV.fetch("ALLOWED_TELEGRAM_USER_IDS", "").split(",").map(&:strip).reject(&:empty?)
    end

    def fetch_integer(key, default: nil)
      value = ENV.fetch(key, nil)
      value = default if value.nil? || value == ""
      integer = Integer(value)
      raise ArgumentError, "#{key} must be positive" unless integer.positive?

      integer
    rescue ArgumentError, TypeError
      raise ArgumentError, "#{key} is invalid"
    end

    def fetch_string(key, default: nil)
      value = ENV.fetch(key, nil)
      value = default if value.nil?
      raise ArgumentError, "#{key} is required" if value.nil? || value.strip.empty?

      value
    end

    def fetch_url(key)
      value = fetch_string(key)
      uri = URI.parse(value)
      raise ArgumentError, "#{key} must be an absolute URL" unless uri.is_a?(URI::HTTP) && uri.host

      value
    rescue URI::InvalidURIError
      raise ArgumentError, "#{key} must be an absolute URL"
    end
  end
end
