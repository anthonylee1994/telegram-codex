require "rails_helper"

RSpec.describe Telegram::MediaGroupStore do
  let(:store) { described_class.new }

  describe "#enqueue" do
    it "persists the buffer deadline and message payload" do
      message = build_message(update_id: 10, message_id: 20, image_file_ids: ["img-1"], text: "caption")

      result = store.enqueue(message, wait_duration_seconds: 0.05)

      expect(result.fetch(:key)).to eq("chat-1:album-1")
      expect(MediaGroupBuffer.find_by(key: "chat-1:album-1")&.deadline_at).to eq(result.fetch(:deadline_at))
      stored_message = MediaGroupMessage.find_by(update_id: 10)
      expect(stored_message&.media_group_key).to eq("chat-1:album-1")
      expect(JSON.parse(stored_message&.payload || "{}")).to include(
        "chat_id" => "chat-1",
        "image_file_ids" => ["img-1"],
        "message_id" => 20,
        "text" => "caption",
        "update_id" => 10
      )
    end
  end

  describe "#flush" do
    it "returns stale when a newer deadline has replaced the scheduled one" do
      message = build_message(update_id: 10, message_id: 20, image_file_ids: ["img-1"])
      result = store.enqueue(message, wait_duration_seconds: 0.05)
      travel 0.01.seconds
      store.enqueue(build_message(update_id: 11, message_id: 21, image_file_ids: ["img-2"]), wait_duration_seconds: 0.05)

      flush_result = store.flush(result.fetch(:key), expected_deadline_at: result.fetch(:deadline_at))

      expect(flush_result).to eq(status: :stale)
    end

    it "returns pending when the deadline has not arrived yet" do
      result = store.enqueue(build_message(update_id: 10, message_id: 20, image_file_ids: ["img-1"]), wait_duration_seconds: 0.05)

      flush_result = store.flush(result.fetch(:key), expected_deadline_at: result.fetch(:deadline_at))

      expect(flush_result.fetch(:status)).to eq(:pending)
      expect(flush_result.fetch(:wait_duration_seconds)).to be > 0
    end

    it "aggregates and removes the buffered rows when the deadline is reached" do
      first = build_message(update_id: 10, message_id: 20, image_file_ids: ["img-1"], text: "caption")
      result = store.enqueue(first, wait_duration_seconds: 0.05)
      store.enqueue(build_message(update_id: 11, message_id: 21, image_file_ids: ["img-2"]), wait_duration_seconds: 0.05)
      travel 1.second

      flush_result = store.flush(result.fetch(:key), expected_deadline_at: MediaGroupBuffer.find("chat-1:album-1").deadline_at)

      expect(flush_result.fetch(:status)).to eq(:ready)
      aggregated_message = flush_result.fetch(:message)
      expect(aggregated_message.image_file_ids).to eq(["img-1", "img-2"])
      expect(aggregated_message.text).to eq("caption")
      expect(aggregated_message.processing_updates).to eq(
        [
          { update_id: 10, message_id: 20 },
          { update_id: 11, message_id: 21 }
        ]
      )
      expect(MediaGroupBuffer.find_by(key: "chat-1:album-1")).to be_nil
      expect(MediaGroupMessage.where(media_group_key: "chat-1:album-1")).to be_empty
    end
  end

  def build_message(update_id:, message_id:, image_file_ids: [], media_group_id: "album-1", text: nil)
    Telegram::InboundMessage.new(
      chat_id: "chat-1",
      image_file_ids: image_file_ids,
      media_group_id: media_group_id,
      message_id: message_id,
      text: text,
      user_id: "user-1",
      update_id: update_id
    )
  end
end
