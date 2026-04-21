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

ActiveRecord::Schema[8.1].define(version: 2026_04_21_000001) do
  create_table "chat_memories", primary_key: "chat_id", id: :string, force: :cascade do |t|
    t.text "memory_text"
    t.integer "updated_at", null: false
    t.index ["chat_id"], name: "index_chat_memories_on_chat_id", unique: true
  end

  create_table "chat_sessions", primary_key: "chat_id", id: :text, force: :cascade do |t|
    t.integer "inflight_started_at"
    t.integer "inflight_update_id"
    t.text "last_response_id"
    t.integer "updated_at", null: false
    t.index ["chat_id"], name: "index_chat_sessions_on_chat_id", unique: true
  end

  create_table "media_group_buffers", primary_key: "key", id: :text, force: :cascade do |t|
    t.integer "deadline_at", null: false
    t.index ["key"], name: "index_media_group_buffers_on_key", unique: true
  end

  create_table "media_group_messages", primary_key: "update_id", force: :cascade do |t|
    t.text "media_group_key", null: false
    t.integer "message_id", null: false
    t.text "payload", null: false
    t.index ["media_group_key"], name: "index_media_group_messages_on_media_group_key"
  end

  create_table "processed_updates", primary_key: "update_id", force: :cascade do |t|
    t.text "chat_id", null: false
    t.text "conversation_state"
    t.integer "message_id", null: false
    t.integer "processed_at", null: false
    t.text "reply_text"
    t.integer "sent_at"
    t.text "suggested_replies"
    t.index ["update_id"], name: "index_processed_updates_on_update_id", unique: true
  end

  add_foreign_key "media_group_messages", "media_group_buffers", column: "media_group_key", primary_key: "key", on_delete: :cascade
end
