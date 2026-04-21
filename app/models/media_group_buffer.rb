# == Schema Information
#
# Table name: media_group_buffers
#
#  key         :text             not null, primary key
#  deadline_at :integer          not null
#
# Indexes
#
#  index_media_group_buffers_on_key  (key) UNIQUE
#

class MediaGroupBuffer < ApplicationRecord
  self.primary_key = :key
end
