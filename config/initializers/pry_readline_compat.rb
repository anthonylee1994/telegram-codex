# frozen_string_literal: true

if defined?(Pry)
  begin
    require 'readline'
  rescue LoadError
    require 'reline'
    Readline = Reline unless defined?(Readline)
  end
end
