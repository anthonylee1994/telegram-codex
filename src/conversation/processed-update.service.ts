import {Injectable, Logger} from "@nestjs/common";
import {TelegramGateway} from "../telegram/telegram.types";
import {InboundMessage} from "../telegram/inbound-message";
import {CONVERSATION_CONSTANTS} from "./conversation.constants";
import {ProcessedUpdateRecord, ProcessedUpdateRepository} from "./processed-update.repository";
import {ReplyResult} from "./reply-result";
import {SessionService} from "./session.service";

@Injectable()
export class ProcessedUpdateService {
    private readonly logger = new Logger(ProcessedUpdateService.name);
    private lastProcessedUpdatePruneAt = 0;

    public constructor(
        private readonly processedUpdateRepository: ProcessedUpdateRepository,
        private readonly sessionService: SessionService
    ) {}

    public find(updateId: number): Promise<ProcessedUpdateRecord | null> {
        return this.processedUpdateRepository.find(updateId);
    }

    public async beginProcessing(message: InboundMessage): Promise<boolean> {
        const claimedUpdateIds: number[] = [];
        for (const processingUpdate of message.processingUpdates) {
            const claimed = await this.processedUpdateRepository.beginProcessing(processingUpdate.update_id, message.chatId, processingUpdate.message_id);
            if (!claimed) {
                await Promise.all(claimedUpdateIds.map(updateId => this.clearProcessing(updateId)));
                return false;
            }
            claimedUpdateIds.push(processingUpdate.update_id);
        }
        return true;
    }

    public clearProcessing(updateId: number): Promise<void> {
        return this.processedUpdateRepository.clearProcessing(updateId);
    }

    public duplicate(processedUpdate: ProcessedUpdateRecord | null): boolean {
        return processedUpdate?.sentAt !== null && processedUpdate?.sentAt !== undefined;
    }

    public replayable(processedUpdate: ProcessedUpdateRecord | null): boolean {
        return Boolean(processedUpdate?.replyText && processedUpdate.conversationState);
    }

    public async resendPendingReply(message: InboundMessage, processedUpdate: ProcessedUpdateRecord, telegramClient: TelegramGateway): Promise<void> {
        await telegramClient.sendMessage(message.chatId, processedUpdate.replyText, this.parseStoredSuggestedReplies(processedUpdate.suggestedReplies), false);
        await this.sessionService.persistConversationState(message.chatId, processedUpdate.conversationState);
        await this.markProcessed(message);
    }

    public markProcessed(updateId: number, chatId: string, messageId: number): Promise<void>;
    public markProcessed(message: InboundMessage): Promise<void>;
    public async markProcessed(updateIdOrMessage: number | InboundMessage, chatId?: string, messageId?: number): Promise<void> {
        if (typeof updateIdOrMessage === "number") {
            await this.processedUpdateRepository.markProcessed(updateIdOrMessage, chatId!, messageId!);
            return;
        }
        for (const processingUpdate of updateIdOrMessage.processingUpdates) {
            await this.processedUpdateRepository.markProcessed(processingUpdate.update_id, updateIdOrMessage.chatId, processingUpdate.message_id);
        }
    }

    public savePendingReply(updateId: number, chatId: string, messageId: number, result: ReplyResult): Promise<void> {
        return this.processedUpdateRepository.savePendingReply(updateId, chatId, messageId, result);
    }

    public async pruneIfNeeded(): Promise<void> {
        const now = Date.now();
        if (this.lastProcessedUpdatePruneAt !== 0 && now - this.lastProcessedUpdatePruneAt < CONVERSATION_CONSTANTS.processedUpdatePruneIntervalMs) {
            return;
        }
        const cutoff = now - CONVERSATION_CONSTANTS.processedUpdateRetentionMs;
        const deletedCount = await this.processedUpdateRepository.pruneSentBefore(cutoff);
        this.lastProcessedUpdatePruneAt = now;
        this.logger.log(`Pruned processed updates count=${deletedCount} cutoff=${cutoff}`);
    }

    public parseStoredSuggestedReplies(rawSuggestedReplies: string | null | undefined): string[] {
        if (!rawSuggestedReplies?.trim()) {
            return [];
        }
        try {
            const replies = JSON.parse(rawSuggestedReplies) as unknown[];
            return replies.filter((reply): reply is string => typeof reply === "string" && reply.trim().length > 0).map(reply => reply.trim());
        } catch {
            return [];
        }
    }
}
