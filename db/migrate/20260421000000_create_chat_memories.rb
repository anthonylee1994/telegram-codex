class CreateChatMemories < ActiveRecord::Migration[8.1]
  def up
    return if table_exists?(:chat_memories)

    create_table :chat_memories, id: false do |t|
      t.string :chat_id, null: false, primary_key: true
      t.text :memory_text
      t.integer :updated_at, null: false
    end

    add_index :chat_memories, :chat_id, unique: true
  end

  def down
    drop_table :chat_memories if table_exists?(:chat_memories)
  end
end
