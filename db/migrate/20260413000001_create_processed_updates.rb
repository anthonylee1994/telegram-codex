# frozen_string_literal: true

class CreateProcessedUpdates < ActiveRecord::Migration[8.1]
  def up
    unless table_exists?(:processed_updates)
      create_table :processed_updates, id: false do |t|
        t.integer :update_id, null: false, primary_key: true
        t.string :chat_id, null: false
        t.integer :message_id, null: false
        t.integer :processed_at, null: false
        t.text :reply_text
        t.text :conversation_state
        t.integer :sent_at
      end
    end

    add_column :processed_updates, :reply_text, :text unless column_exists?(:processed_updates, :reply_text)
    add_column :processed_updates, :conversation_state, :text unless column_exists?(:processed_updates, :conversation_state)
    add_column :processed_updates, :sent_at, :integer unless column_exists?(:processed_updates, :sent_at)
    add_index :processed_updates, :update_id, unique: true unless index_exists?(:processed_updates, :update_id, unique: true)
  end

  def down
    drop_table :processed_updates if table_exists?(:processed_updates)
  end
end
