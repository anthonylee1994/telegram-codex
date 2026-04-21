class CreateMediaGroupStoreTables < ActiveRecord::Migration[8.1]
  def change
    create_table :media_group_buffers, primary_key: :key, id: :text do |t|
      t.integer :deadline_at, null: false
    end

    add_index :media_group_buffers, :key, unique: true

    create_table :media_group_messages, primary_key: :update_id, id: :integer do |t|
      t.text :media_group_key, null: false
      t.integer :message_id, null: false
      t.text :payload, null: false
    end

    add_index :media_group_messages, :media_group_key
    add_foreign_key :media_group_messages, :media_group_buffers, column: :media_group_key, primary_key: :key, on_delete: :cascade
  end
end
