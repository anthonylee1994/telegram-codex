# == Schema Information
#
# Table name: processed_updates
#
#  update_id          :integer          not null, primary key
#  chat_id            :text             not null
#  message_id         :integer          not null
#  processed_at       :integer          not null
#  reply_text         :text
#  conversation_state :text
#  sent_at            :integer
#  suggested_replies  :text
#
# Indexes
#
#  index_processed_updates_on_update_id  (update_id) UNIQUE
#

class ProcessedUpdate < ApplicationRecord
  self.primary_key = :update_id
end
