# == Schema Information
#
# Table name: chat_memories
#
#  chat_id     :string           not null, primary key
#  memory_text :text
#  updated_at  :integer          not null
#
# Indexes
#
#  index_chat_memories_on_chat_id  (chat_id) UNIQUE
#

class ChatMemory < ApplicationRecord
  self.primary_key = :chat_id
end
