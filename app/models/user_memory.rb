# == Schema Information
#
# Table name: user_memories
#
#  id               :integer          not null, primary key
#  telegram_user_id :text             not null
#  kind             :text             not null
#  key              :text             not null
#  value            :text             not null
#  created_at       :integer          not null
#  updated_at       :integer          not null
#  last_used_at     :integer
#
# Indexes
#
#  index_user_memories_on_telegram_user_id                   (telegram_user_id)
#  index_user_memories_on_telegram_user_id_and_kind_and_key  (telegram_user_id,kind,key) UNIQUE
#

class UserMemory < ApplicationRecord
end
