require "rails_helper"

RSpec.describe Telegram::MediaGroupAggregator do
  describe "#call" do
    let(:flush_job_class) { class_double(MediaGroupFlushJob) }
    let(:job_proxy) { double("ActiveJob::ConfiguredJob", perform_later: true) }
    let(:media_group_store) { instance_double(Telegram::MediaGroupStore) }
    let(:aggregator) do
      described_class.new(
        wait_duration_seconds: 0.03,
        media_group_store: media_group_store,
        flush_job_class: flush_job_class
      )
    end

    it "returns non-media-group messages unchanged" do
      message = build_message(update_id: 1, message_id: 10, media_group_id: nil, text: "hello")

      expect(aggregator.call(message)).to eq(message)
    end

    it "stores the message and schedules a delayed flush job" do
      message = build_message(update_id: 10, message_id: 20, image_file_ids: ["img-1"], text: "caption")
      allow(media_group_store).to receive(:enqueue).and_return(
        {
          key: "chat-1:album-1",
          deadline_at: 123_456
        }
      )
      allow(flush_job_class).to receive(:set).with(wait: 0.03).and_return(job_proxy)

      result = aggregator.call(message)

      expect(result).to equal(described_class::DEFERRED)
      expect(media_group_store).to have_received(:enqueue).with(message, wait_duration_seconds: 0.03)
      expect(job_proxy).to have_received(:perform_later).with("chat-1:album-1", 123_456)
    end

    it "clears the shared store on reset" do
      allow(media_group_store).to receive(:clear!)

      aggregator.reset!

      expect(media_group_store).to have_received(:clear!)
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
