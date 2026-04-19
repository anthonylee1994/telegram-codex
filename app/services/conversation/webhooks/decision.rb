class Conversation::Webhooks::Decision
  START_COMMAND_PATTERN = %r{\A/start(?:@[\w_]+)?\z}u
  HELP_COMMAND_PATTERN = %r{\A/help(?:@[\w_]+)?\z}u
  NEW_SESSION_COMMAND_PATTERN = %r{\A/new(?:@[\w_]+)?\z}u
  SESSION_COMMAND_PATTERN = %r{\A/session(?:@[\w_]+)?\z}u
  STATUS_COMMAND_PATTERN = %r{\A/status(?:@[\w_]+)?\z}u
  SUMMARY_COMMAND_PATTERN = %r{\A/summary(?:@[\w_]+)?\z}u

  attr_reader :action, :message, :processed_update, :response_text

  def initialize(action:, message: nil, processed_update: nil, response_text: nil)
    @action = action
    @message = message
    @processed_update = processed_update
    @response_text = response_text
  end

  def self.unsupported
    new(action: :unsupported)
  end

  def self.duplicate(message)
    new(action: :duplicate, message: message)
  end

  def self.replay(message, processed_update)
    new(action: :replay, message: message, processed_update: processed_update)
  end

  def self.reject_unauthorized(message)
    new(action: :reject_unauthorized, message: message)
  end

  def self.reset_session(message, response_text)
    new(action: :reset_session, message: message, response_text: response_text)
  end

  def self.show_help(message)
    new(action: :show_help, message: message)
  end

  def self.show_status(message)
    new(action: :show_status, message: message)
  end

  def self.show_session(message)
    new(action: :show_session, message: message)
  end

  def self.summarize_session(message, response_text)
    new(action: :summarize_session, message: message, response_text: response_text)
  end

  def self.rate_limited(message)
    new(action: :rate_limited, message: message)
  end

  def self.too_many_images(message, response_text)
    new(action: :too_many_images, message: message, response_text: response_text)
  end

  def self.generate_reply(message)
    new(action: :generate_reply, message: message)
  end

  def unsupported?
    action == :unsupported
  end

  def duplicate?
    action == :duplicate
  end

  def replay?
    action == :replay
  end

  def reject_unauthorized?
    action == :reject_unauthorized
  end

  def reset_session?
    action == :reset_session
  end

  def rate_limited?
    action == :rate_limited
  end

  def show_help?
    action == :show_help
  end

  def show_status?
    action == :show_status
  end

  def show_session?
    action == :show_session
  end

  def summarize_session?
    action == :summarize_session
  end

  def too_many_images?
    action == :too_many_images
  end

  class Resolver
    def initialize(
      processed_update_flow:,
      rate_limiter:,
      config:,
      start_message:,
      new_session_message:,
      too_many_images_message:,
      summary_queued_message:
    )
      @processed_update_flow = processed_update_flow
      @rate_limiter = rate_limiter
      @config = config
      @start_message = start_message
      @new_session_message = new_session_message
      @too_many_images_message = too_many_images_message
      @summary_queued_message = summary_queued_message
    end

    def call(message)
      return Conversation::Webhooks::Decision.unsupported if unsupported_message?(message)

      processed_update = @processed_update_flow.find(message.update_id)
      return Conversation::Webhooks::Decision.duplicate(message) if @processed_update_flow.duplicate?(processed_update)
      return Conversation::Webhooks::Decision.replay(message, processed_update) if @processed_update_flow.replayable?(processed_update)
      return Conversation::Webhooks::Decision.reject_unauthorized(message) if unauthorized_user?(message.user_id)
      return Conversation::Webhooks::Decision.reset_session(message, @new_session_message) if new_session_command?(message.text)
      return Conversation::Webhooks::Decision.reset_session(message, @start_message) if start_command?(message.text)
      return Conversation::Webhooks::Decision.show_help(message) if help_command?(message.text)
      return Conversation::Webhooks::Decision.show_status(message) if status_command?(message.text)
      return Conversation::Webhooks::Decision.show_session(message) if session_command?(message.text)
      return Conversation::Webhooks::Decision.summarize_session(message, @summary_queued_message) if summary_command?(message.text)
      return Conversation::Webhooks::Decision.too_many_images(message, @too_many_images_message) if too_many_media_group_images?(message)
      return Conversation::Webhooks::Decision.rate_limited(message) unless @rate_limiter.allow(message.chat_id)
      return Conversation::Webhooks::Decision.duplicate(message) unless @processed_update_flow.begin_processing(message)

      Conversation::Webhooks::Decision.generate_reply(message)
    end

    private

    def unsupported_message?(message)
      message.nil? || message.unsupported?
    end

    def unauthorized_user?(user_id)
      @config.allowed_telegram_user_ids.any? && !@config.allowed_telegram_user_ids.include?(user_id)
    end

    def new_session_command?(text)
      text.match?(NEW_SESSION_COMMAND_PATTERN)
    end

    def help_command?(text)
      text.match?(HELP_COMMAND_PATTERN)
    end

    def status_command?(text)
      text.match?(STATUS_COMMAND_PATTERN)
    end

    def session_command?(text)
      text.match?(SESSION_COMMAND_PATTERN)
    end

    def summary_command?(text)
      text.match?(SUMMARY_COMMAND_PATTERN)
    end

    def start_command?(text)
      text.match?(START_COMMAND_PATTERN)
    end

    def too_many_media_group_images?(message)
      message.media_group? && message.image_count > @config.max_media_group_images
    end
  end
end
