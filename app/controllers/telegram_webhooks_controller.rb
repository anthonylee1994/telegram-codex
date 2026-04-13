# frozen_string_literal: true

class TelegramWebhooksController < ApplicationController
  def create
    if request.headers['X-Telegram-Bot-Api-Secret-Token'] != AppConfig.fetch.telegram_webhook_secret
      Rails.logger.warn('Rejected Telegram webhook with invalid secret')
      render json: { ok: false }, status: :unauthorized
      return
    end

    TelegramWebhookHandler.new.handle(request.request_parameters.presence || parsed_raw_body)
    render json: { ok: true }
  rescue StandardError => e
    Rails.logger.error("Failed to process Telegram webhook: #{e.message}")
    render json: { ok: false }, status: :internal_server_error
  end

  private

  def parsed_raw_body
    JSON.parse(request.raw_post.presence || '{}')
  rescue JSON::ParserError
    {}
  end
end
