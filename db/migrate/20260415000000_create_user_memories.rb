class CreateUserMemories < ActiveRecord::Migration[8.1]
  def change
    create_table :user_memories do |t|
      t.text :telegram_user_id, null: false
      t.text :kind, null: false
      t.text :key, null: false
      t.text :value, null: false
      t.integer :created_at, null: false
      t.integer :updated_at, null: false
      t.integer :last_used_at

      t.index [ :telegram_user_id, :kind, :key ], unique: true
      t.index :telegram_user_id
    end
  end
end
