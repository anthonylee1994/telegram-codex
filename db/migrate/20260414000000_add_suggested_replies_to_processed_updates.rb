class AddSuggestedRepliesToProcessedUpdates < ActiveRecord::Migration[8.1]
  def up
    add_column :processed_updates, :suggested_replies, :text unless column_exists?(:processed_updates, :suggested_replies)
  end

  def down
    remove_column :processed_updates, :suggested_replies if column_exists?(:processed_updates, :suggested_replies)
  end
end
