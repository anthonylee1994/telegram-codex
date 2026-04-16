class WebhookDecision
  START_COMMAND_PATTERN = %r{\A/start(?:@[\w_]+)?\z}u
  NEW_SESSION_COMMAND_PATTERN = %r{\A/new(?:@[\w_]+)?\z}u

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

  def self.rate_limited(message)
    new(action: :rate_limited, message: message)
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

  class Resolver
    def initialize(processed_update_flow:, rate_limiter:, config:, start_message:, new_session_message:)
      @processed_update_flow = processed_update_flow
      @rate_limiter = rate_limiter
      @config = config
      @start_message = start_message
      @new_session_message = new_session_message
    end

    def call(message)
      return WebhookDecision.unsupported if unsupported_message?(message)

      processed_update = @processed_update_flow.find(message.update_id)
      return WebhookDecision.duplicate(message) if @processed_update_flow.duplicate?(processed_update)
      return WebhookDecision.replay(message, processed_update) if @processed_update_flow.replayable?(processed_update)
      return WebhookDecision.reject_unauthorized(message) if unauthorized_user?(message.user_id)
      return WebhookDecision.reset_session(message, @new_session_message) if new_session_command?(message.text)
      return WebhookDecision.reset_session(message, @start_message) if start_command?(message.text)
      return WebhookDecision.rate_limited(message) unless @rate_limiter.allow(message.chat_id)
      return WebhookDecision.duplicate(message) unless @processed_update_flow.begin_processing(message)

      WebhookDecision.generate_reply(message)
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

    def start_command?(text)
      text.match?(START_COMMAND_PATTERN)
    end
  end
end
