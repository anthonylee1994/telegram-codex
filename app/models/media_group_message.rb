# == Schema Information
#
# Table name: media_group_messages
#
#  update_id       :integer          not null, primary key
#  media_group_key :text             not null
#  message_id      :integer          not null
#  payload         :text             not null
#
# Indexes
#
#  index_media_group_messages_on_media_group_key  (media_group_key)
#

class MediaGroupMessage < ApplicationRecord
  self.primary_key = :update_id
end
