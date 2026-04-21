require "rails_helper"

RSpec.describe MediaGroupFlushJob do
  let(:store) { instance_double(Telegram::MediaGroupStore) }
  let(:handler) { instance_double(Telegram::WebhookHandler, handle_inbound_message: true) }
  let(:message) do
    Telegram::InboundMessage.new(
      chat_id: "chat-1",
      image_file_ids: ["img-1"],
      media_group_id: "album-1",
      message_id: 20,
      text: "caption",
      user_id: "user-1",
      update_id: 10
    )
  end

  before do
    allow(Telegram::MediaGroupStore).to receive(:new).and_return(store)
    allow(Telegram::WebhookHandlerFactory).to receive(:build).and_return(handler)
  end

  it "passes a ready aggregated message to the webhook handler" do
    allow(store).to receive(:flush).and_return(status: :ready, message: message)

    described_class.perform_now("chat-1:album-1", 123_456)

    expect(store).to have_received(:flush).with("chat-1:album-1", expected_deadline_at: 123_456)
    expect(handler).to have_received(:handle_inbound_message).with(message)
  end

  it "reschedules itself when the deadline is still pending" do
    allow(store).to receive(:flush).and_return(status: :pending, wait_duration_seconds: 0.25)

    expect {
      described_class.perform_now("chat-1:album-1", 123_456)
    }.to have_enqueued_job(described_class).with("chat-1:album-1", 123_456)
  end

  it "does nothing when the flush is stale or missing" do
    allow(store).to receive(:flush).and_return(status: :stale)

    described_class.perform_now("chat-1:album-1", 123_456)

    expect(handler).not_to have_received(:handle_inbound_message)
  end
end
