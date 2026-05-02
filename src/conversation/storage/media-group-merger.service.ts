import {Injectable} from "@nestjs/common";
import {InboundMessage} from "../../telegram/shared/inbound-message";

@Injectable()
export class MediaGroupMergerService {
    public merge(messages: InboundMessage[]): InboundMessage {
        if (messages.length === 0) {
            throw new Error("Cannot merge empty message list");
        }
        const sorted = [...messages].sort((left, right) => left.messageId - right.messageId || left.updateId - right.updateId);
        const primary = sorted[0];
        const text = sorted.map(message => message.text).find(value => value?.trim()) ?? null;
        const imageFileIds = [...new Set(sorted.flatMap(message => message.imageFileIds))];
        return InboundMessage.forMergedMediaGroup(
            primary,
            imageFileIds,
            sorted.map(message => ({update_id: message.updateId, message_id: message.messageId})),
            text
        );
    }
}
