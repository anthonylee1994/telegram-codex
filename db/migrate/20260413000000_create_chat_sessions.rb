class CreateChatSessions < ActiveRecord::Migration[8.1]
  def up
    unless table_exists?(:chat_sessions)
      create_table :chat_sessions, id: false do |t|
        t.string :chat_id, null: false, primary_key: true
        t.text :last_response_id
        t.integer :updated_at, null: false
      end
    end

    add_index :chat_sessions, :chat_id, unique: true unless index_exists?(:chat_sessions, :chat_id, unique: true)
  end

  def down
    drop_table :chat_sessions if table_exists?(:chat_sessions)
  end
end
