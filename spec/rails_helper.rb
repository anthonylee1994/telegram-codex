ENV['RAILS_ENV'] ||= 'test'
ENV['PORT'] ||= '3000'
ENV['BASE_URL'] ||= 'https://example.com'
ENV['TELEGRAM_BOT_TOKEN'] ||= 'token'
ENV['TELEGRAM_WEBHOOK_SECRET'] ||= 'expected-secret'
ENV['ALLOWED_TELEGRAM_USER_IDS'] ||= ''
ENV['SQLITE_DB_PATH'] ||= File.expand_path('../data/test.db', __dir__)
ENV['SESSION_TTL_DAYS'] ||= '7'
ENV['RATE_LIMIT_WINDOW_MS'] ||= '10000'
ENV['RATE_LIMIT_MAX_MESSAGES'] ||= '5'

require 'spec_helper'
require File.expand_path('../config/environment', __dir__)

abort('The Rails environment is running in production mode!') if Rails.env.production?

require 'rspec/rails'
require 'active_job/test_helper'
require 'active_support/testing/time_helpers'
Dir[Rails.root.join('spec/support/**/*.rb')].sort.each { |file| require file }

begin
  ActiveRecord::Migration.maintain_test_schema!
rescue ActiveRecord::PendingMigrationError => e
  abort(e.to_s.strip)
end

RSpec.configure do |config|
  config.fixture_paths = [Rails.root.join('spec/fixtures')]
  config.use_transactional_fixtures = true
  config.filter_rails_from_backtrace!
  config.include TelegramWebhookHandlerTestHelper
  config.include ActiveJob::TestHelper
  config.include ActiveSupport::Testing::TimeHelpers

  config.before do
    AppConfig.reset!
    Conversation::ChatRateLimiter.instance.reset!
    Telegram::WebhookHandlerFactory.reset!
    clear_enqueued_jobs
    clear_performed_jobs
  end
end
