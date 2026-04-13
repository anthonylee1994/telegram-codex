# frozen_string_literal: true

Rails.application.routes.draw do
  get '/health', to: 'health#show'
  post '/telegram/webhook', to: 'telegram_webhooks#create'
end
