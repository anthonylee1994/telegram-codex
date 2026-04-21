# == Schema Information
#
# Table name: chat_sessions
#
#  chat_id             :text             primary key
#  last_response_id    :text
#  updated_at          :integer          not null
#  inflight_update_id  :integer
#  inflight_started_at :integer
#
# Indexes
#
#  index_chat_sessions_on_chat_id  (chat_id) UNIQUE
#

class ChatSession < ApplicationRecord
  self.primary_key = :chat_id
end
