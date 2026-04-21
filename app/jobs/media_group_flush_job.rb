class MediaGroupFlushJob < ApplicationJob
  retry_on StandardError, wait: 1.second, attempts: 3

  def perform(key, expected_deadline_at)
    result = media_group_store.flush(key, expected_deadline_at: expected_deadline_at)

    case result.fetch(:status)
    when :ready
      Telegram::WebhookHandlerFactory.build.handle_inbound_message(result.fetch(:message))
    when :pending
      self.class.set(wait: result.fetch(:wait_duration_seconds)).perform_later(key, expected_deadline_at)
    end
  end

  private

  def media_group_store
    @media_group_store ||= Telegram::MediaGroupStore.new
  end
end
