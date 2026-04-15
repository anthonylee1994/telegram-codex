# This file is auto-generated from the current state of the database. Instead
# of editing this file, please use the migrations feature of Active Record to
# incrementally modify your database, and then regenerate this schema definition.
#
# This file is the source Rails uses to define your schema when running `bin/rails
# db:schema:load`. When creating a new database, `bin/rails db:schema:load` tends to
# be faster and is potentially less error prone than running all of your
# migrations from scratch. Old migrations may fail to apply correctly if those
# migrations use external dependencies or application code.
#
# It's strongly recommended that you check this file into your version control system.

ActiveRecord::Schema[8.1].define(version: 2026_04_15_000000) do
  create_table "chat_sessions", primary_key: "chat_id", id: :text, force: :cascade do |t|
    t.text "last_response_id"
    t.integer "updated_at", null: false
    t.index ["chat_id"], name: "index_chat_sessions_on_chat_id", unique: true
  end

  create_table "processed_updates", primary_key: "update_id", id: :integer, default: nil, force: :cascade do |t|
    t.text "chat_id", null: false
    t.text "conversation_state"
    t.integer "message_id", null: false
    t.integer "processed_at", null: false
    t.text "reply_text"
    t.integer "sent_at"
    t.text "suggested_replies"
    t.index ["update_id"], name: "index_processed_updates_on_update_id", unique: true
  end

  create_table "user_memories", force: :cascade do |t|
    t.integer "created_at", null: false
    t.text "key", null: false
    t.text "kind", null: false
    t.integer "last_used_at"
    t.text "telegram_user_id", null: false
    t.integer "updated_at", null: false
    t.text "value", null: false
    t.index ["telegram_user_id", "kind", "key"], name: "index_user_memories_on_telegram_user_id_and_kind_and_key", unique: true
    t.index ["telegram_user_id"], name: "index_user_memories_on_telegram_user_id"
  end
end
