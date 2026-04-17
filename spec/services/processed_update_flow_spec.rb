require "rails_helper"

RSpec.describe ProcessedUpdateFlow do
  let(:conversation_service) { ConversationService.new(reply_client: instance_double(CodexCliClient)) }
  let(:flow) { described_class.new(conversation_service: conversation_service) }

  describe "#begin_processing" do
    it "claims every update that belongs to an aggregated album message" do
      message = InboundTelegramMessage.new(
        callback_query_id: nil,
        chat_id: "chat-1",
        image_file_ids: [ "image-1", "image-2" ],
        inline_callback: false,
        media_group_id: "album-1",
        message_id: 10,
        processing_updates: [
          { update_id: 100, message_id: 10 },
          { update_id: 101, message_id: 11 }
        ],
        text: "幫我比較",
        user_id: "234392020",
        update_id: 100
      )

      expect(flow.begin_processing(message)).to eq(true)
      expect(ProcessedUpdate.find_by(update_id: 100)).to be_present
      expect(ProcessedUpdate.find_by(update_id: 101)).to be_present

      flow.mark_processed(message)

      expect(ProcessedUpdate.find_by(update_id: 100)&.sent_at).to be_present
      expect(ProcessedUpdate.find_by(update_id: 101)&.sent_at).to be_present
    end
  end
end
