# frozen_string_literal: true

require 'singleton'

class ChatRateLimiter
  include Singleton

  def initialize
    reset!
  end

  def allow(chat_id, now: current_time_ms)
    config = AppConfig.fetch

    @mutex.synchronize do
      fresh_hits = (@hits[chat_id] || []).select { |timestamp| now - timestamp < config.rate_limit_window_ms }

      if fresh_hits.length >= config.rate_limit_max_messages
        @hits[chat_id] = fresh_hits
        return false
      end

      fresh_hits << now
      @hits[chat_id] = fresh_hits
    end

    true
  end

  def reset!
    @hits = {}
    @mutex = Mutex.new
  end

  private

  def current_time_ms
    (Time.now.to_f * 1000).to_i
  end
end
