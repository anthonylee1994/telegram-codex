class Telegram::MediaGroupAggregator
  DEFERRED = Object.new
  DEFAULT_WAIT_DURATION_SECONDS = 0.35

  def self.reset!
    Telegram::MediaGroupStore.new.clear!
    Telegram::WebhookHandlerFactory.reset!
  end

  def initialize(
    wait_duration_seconds: DEFAULT_WAIT_DURATION_SECONDS,
    media_group_store: Telegram::MediaGroupStore.new,
    flush_job_class: MediaGroupFlushJob
  )
    @wait_duration_seconds = wait_duration_seconds
    @media_group_store = media_group_store
    @flush_job_class = flush_job_class
  end

  def reset!
    @media_group_store.clear!
  end

  def call(message)
    return message unless message&.media_group?

    result = @media_group_store.enqueue(message, wait_duration_seconds: @wait_duration_seconds)
    @flush_job_class.set(wait: @wait_duration_seconds).perform_later(result.fetch(:key), result.fetch(:deadline_at))
    DEFERRED
  end
end
