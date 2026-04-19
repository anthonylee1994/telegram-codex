class Telegram::MediaGroupAggregator
  DEFERRED = Object.new
  DEFAULT_WAIT_DURATION_SECONDS = 0.35

  def initialize(wait_duration_seconds: DEFAULT_WAIT_DURATION_SECONDS)
    @wait_duration_seconds = wait_duration_seconds
  end

  class << self
    def reset!
      mutex.synchronize do
        @entries = {}
      end
    end

    private

    def entries
      @entries ||= {}
    end

    def mutex
      @mutex ||= Mutex.new
    end
  end

  def call(message, &processor)
    return message unless message&.media_group?

    key = build_key(message)
    enqueue_message(key, message, processor)
    DEFERRED
  end

  private

  def enqueue_message(key, message, processor)
    should_schedule = false

    self.class.send(:mutex).synchronize do
      entry = self.class.send(:entries)[key] ||= {
        deadline_at: monotonic_now + @wait_duration_seconds,
        messages: {},
        processor: processor,
        scheduled: false
      }
      entry[:deadline_at] = monotonic_now + @wait_duration_seconds
      entry[:messages][message.update_id] = message
      entry[:processor] = processor if processor
      unless entry[:scheduled]
        entry[:scheduled] = true
        should_schedule = true
      end
    end

    schedule_flush(key) if should_schedule
  end

  def schedule_flush(key)
    Thread.new do
      loop do
        deadline_at = self.class.send(:mutex).synchronize do
          self.class.send(:entries).dig(key, :deadline_at)
        end

        break if deadline_at.nil?

        wait_duration = deadline_at - monotonic_now
        sleep(wait_duration) if wait_duration.positive?

        processor, aggregated_message = finalize_if_ready(key)
        next if aggregated_message.nil?

        processor&.call(aggregated_message)
        break
      end
    rescue StandardError => e
      Rails.logger.error("Failed to flush media group key=#{key} error=#{e.message}")
    end
  end

  def finalize_if_ready(key)
    self.class.send(:mutex).synchronize do
      entry = self.class.send(:entries)[key]
      return [nil, nil] if entry.nil?
      return [nil, nil] if monotonic_now < entry[:deadline_at]

      self.class.send(:entries).delete(key)
      [entry[:processor], aggregate_messages(entry.fetch(:messages).values)]
    end
  end

  def aggregate_messages(messages)
    ordered_messages = Array(messages).sort_by { |message| [message.message_id, message.update_id] }
    primary_message = ordered_messages.first
    aggregated_text = ordered_messages.filter_map { |message| message.text.presence }.first.to_s
    aggregated_image_file_ids = ordered_messages.flat_map(&:image_file_ids).uniq
    processing_updates = ordered_messages.map do |message|
      { update_id: message.update_id, message_id: message.message_id }
    end

    Telegram::InboundMessage.new(
      chat_id: primary_message.chat_id,
      image_file_ids: aggregated_image_file_ids,
      media_group_id: primary_message.media_group_id,
      message_id: primary_message.message_id,
      processing_updates: processing_updates,
      text: aggregated_text,
      user_id: primary_message.user_id,
      update_id: primary_message.update_id
    )
  end

  def build_key(message)
    "#{message.chat_id}:#{message.media_group_id}"
  end

  def monotonic_now
    Process.clock_gettime(Process::CLOCK_MONOTONIC)
  end
end
