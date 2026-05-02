import {Injectable} from "@nestjs/common";
import {InjectRepository} from "@nestjs/typeorm";
import {Repository} from "typeorm";
import {MediaGroupBufferEntity, MediaGroupMessageEntity} from "../../database/entities";
import {InboundMessage} from "../../telegram/shared/inbound-message";
import {MediaGroupMergerService} from "./media-group-merger.service";

export type FlushResult = {kind: "missing"} | {kind: "stale"} | {kind: "pending"; waitDurationSeconds: number} | {kind: "ready"; message: InboundMessage};

@Injectable()
export class MediaGroupBufferRepository {
    public constructor(
        @InjectRepository(MediaGroupBufferEntity)
        private readonly bufferRepository: Repository<MediaGroupBufferEntity>,
        @InjectRepository(MediaGroupMessageEntity)
        private readonly messageRepository: Repository<MediaGroupMessageEntity>,
        private readonly mediaGroupMerger: MediaGroupMergerService
    ) {}

    public async enqueue(message: InboundMessage, waitDurationSeconds: number): Promise<{deadlineAt: number; key: string}> {
        const deadlineAt = Date.now() + Math.round(waitDurationSeconds * 1000);
        const key = this.buildKey(message);
        await this.bufferRepository.save({key, deadlineAt});
        await this.messageRepository.save({
            updateId: message.updateId,
            mediaGroupKey: key,
            messageId: message.messageId,
            payload: JSON.stringify(message),
        });
        return {deadlineAt, key};
    }

    public async flush(key: string, expectedDeadlineAt: number): Promise<FlushResult> {
        const buffer = await this.bufferRepository.findOneBy({key});
        if (!buffer) {
            return {kind: "missing"};
        }
        if (Number(buffer.deadlineAt) !== expectedDeadlineAt) {
            return {kind: "stale"};
        }
        const waitDurationMs = Number(buffer.deadlineAt) - Date.now();
        if (waitDurationMs > 0) {
            return {kind: "pending", waitDurationSeconds: waitDurationMs / 1000};
        }
        const rows = await this.messageRepository.find({
            where: {mediaGroupKey: key},
            order: {messageId: "ASC", updateId: "ASC"},
        });
        await this.messageRepository.delete({mediaGroupKey: key});
        await this.bufferRepository.delete({key});
        if (rows.length === 0) {
            return {kind: "missing"};
        }
        return {
            kind: "ready",
            message: this.mediaGroupMerger.merge(rows.map(row => InboundMessage.fromJSON(JSON.parse(row.payload) as unknown))),
        };
    }

    private buildKey(message: InboundMessage): string {
        return `${message.chatId}:${message.mediaGroupId}`;
    }
}
