import {Injectable} from "@nestjs/common";
import {InjectRepository} from "@nestjs/typeorm";
import {IsNull, LessThan, Not, Repository} from "typeorm";
import {ProcessedUpdateEntity} from "../../database/entities";
import {ReplyResult} from "../reply/reply-result";

export interface ProcessedUpdateRecord {
    updateId: number;
    chatId: string | null;
    messageId: number;
    processedAt: number;
    replyText: string | null;
    conversationState: string | null;
    suggestedReplies: string | null;
    sentAt: number | null;
}

@Injectable()
export class ProcessedUpdateRepository {
    private static readonly inflightTimeoutMs = 5 * 60 * 1000;

    public constructor(
        @InjectRepository(ProcessedUpdateEntity)
        private readonly repository: Repository<ProcessedUpdateEntity>
    ) {}

    public async find(updateId: number): Promise<ProcessedUpdateRecord | null> {
        const entity = await this.repository.findOneBy({updateId});
        return entity ? this.toRecord(entity) : null;
    }

    public async beginProcessing(updateId: number, chatId: string, messageId: number): Promise<boolean> {
        const now = Date.now();
        const existing = await this.repository.findOneBy({updateId});
        if (!existing) {
            await this.repository.save({
                updateId,
                chatId,
                messageId,
                processedAt: now,
                replyText: null,
                conversationState: null,
                suggestedReplies: null,
                sentAt: null,
            });
            return true;
        }
        if (existing.sentAt !== null || (existing.replyText !== null && existing.conversationState !== null)) {
            return false;
        }
        if (now - Number(existing.processedAt) < ProcessedUpdateRepository.inflightTimeoutMs) {
            return false;
        }
        await this.repository.save({
            ...existing,
            chatId,
            messageId,
            processedAt: now,
            replyText: null,
            conversationState: null,
            suggestedReplies: null,
            sentAt: null,
        });
        return true;
    }

    public async clearProcessing(updateId: number): Promise<void> {
        const entity = await this.repository.findOneBy({updateId});
        if (entity && entity.sentAt === null && entity.replyText === null && entity.conversationState === null) {
            await this.repository.delete({updateId});
        }
    }

    public async markProcessed(updateId: number, chatId: string, messageId: number): Promise<void> {
        const now = Date.now();
        await this.repository.save({
            updateId,
            chatId,
            messageId,
            processedAt: now,
            sentAt: now,
        });
    }

    public async savePendingReply(updateId: number, chatId: string, messageId: number, result: ReplyResult): Promise<void> {
        await this.repository.save({
            updateId,
            chatId,
            messageId,
            processedAt: Date.now(),
            replyText: result.text,
            conversationState: result.conversationState,
            suggestedReplies: JSON.stringify(result.suggestedReplies),
            sentAt: null,
        });
    }

    public async pruneSentBefore(cutoff: number): Promise<number> {
        const result = await this.repository.delete({sentAt: Not(IsNull()), processedAt: LessThan(cutoff)});
        return result.affected ?? 0;
    }

    private toRecord(entity: ProcessedUpdateEntity): ProcessedUpdateRecord {
        return {
            updateId: Number(entity.updateId),
            chatId: entity.chatId,
            messageId: Number(entity.messageId),
            processedAt: Number(entity.processedAt),
            replyText: entity.replyText,
            conversationState: entity.conversationState,
            suggestedReplies: entity.suggestedReplies,
            sentAt: entity.sentAt === null ? null : Number(entity.sentAt),
        };
    }
}
