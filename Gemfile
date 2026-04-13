# frozen_string_literal: true

source 'https://rubygems.org'

ruby '4.0.2'

gem 'dotenv-rails'
gem 'puma', '>= 6.0'
gem 'rails', '~> 8.1.3'
gem 'sqlite3', '>= 2.1'
gem 'tzinfo-data', platforms: %i[windows jruby]

group :development, :test do
  gem 'debug', platforms: %i[mri windows], require: 'debug/prelude'
  gem 'rspec-rails'
  gem 'rubocop', require: false
  gem 'rubocop-performance', require: false
  gem 'rubocop-rails', require: false
end
