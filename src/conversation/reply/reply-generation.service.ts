import {Inject, Injectable, Logger} from "@nestjs/common";
import {CodexMemoryClientService} from "../../codex/memory/codex-memory-client.service";
import {CodexReplyClientService} from "../../codex/reply/codex-reply-client.service";
import {InboundMessage} from "../../telegram/shared/inbound-message";
import {TELEGRAM_GATEWAY} from "../../telegram/shared/telegram.types";
import type {TelegramGateway} from "../../telegram/shared/telegram.types";
import {AttachmentDownloaderService} from "./attachment-downloader.service";
import {ChatMemoryRepository} from "../storage/chat-memory.repository";
import {ChatSessionRepository} from "../storage/chat-session.repository";
import {ProcessedUpdateService} from "./processed-update.service";
import {ReplyResult} from "./reply-result";
import {SessionService} from "../session/session.service";

@Injectable()
export class ReplyGenerationService {
    private readonly logger = new Logger(ReplyGenerationService.name);

    public constructor(
        private readonly replyClient: CodexReplyClientService,
        private readonly chatSessionRepository: ChatSessionRepository,
        private readonly chatMemoryRepository: ChatMemoryRepository,
        private readonly memoryClient: CodexMemoryClientService,
        private readonly processedUpdateService: ProcessedUpdateService,
        private readonly sessionService: SessionService,
        @Inject(TELEGRAM_GATEWAY) private readonly telegramClient: TelegramGateway,
        private readonly attachmentDownloader: AttachmentDownloaderService
    ) {}

    public async handle(message: InboundMessage): Promise<void> {
        try {
            await this.processedUpdateService.pruneIfNeeded();
            const reply = await this.telegramClient.withTypingStatus(message.chatId, () => this.generateReply(message));
            await this.deliverReply(message, reply);
        } catch (error) {
            await this.processedUpdateService.clearProcessing(message.updateId);
            throw error;
        }
    }

    private async generateReply(message: InboundMessage): Promise<ReplyResult> {
        const imageFilePaths = await this.attachmentDownloader.downloadImages(message.effectiveImageFileIds());
        try {
            return await this.replyClient.generateReply(
                message.textOrEmpty(),
                await this.findLastResponseId(message.chatId),
                imageFilePaths,
                message.replyToText,
                await this.findMemoryText(message.chatId)
            );
        } finally {
            await this.attachmentDownloader.cleanup(imageFilePaths);
        }
    }

    private async deliverReply(message: InboundMessage, reply: ReplyResult): Promise<void> {
        await this.processedUpdateService.savePendingReply(message.updateId, message.chatId, message.messageId, reply);
        await this.telegramClient.sendMessage(message.chatId, reply.text, reply.suggestedReplies, false);
        await this.sessionService.persistConversationState(message.chatId, reply.conversationState);
        await this.refreshMemory(message.chatId, message.text, reply.text);
        await this.processedUpdateService.markProcessed(message.updateId, message.chatId, message.messageId);
    }

    private async findLastResponseId(chatId: string): Promise<string | null> {
        return (await this.chatSessionRepository.findActive(chatId))?.lastResponseId ?? null;
    }

    private async findMemoryText(chatId: string): Promise<string | null> {
        return (await this.chatMemoryRepository.find(chatId))?.memoryText ?? null;
    }

    private async refreshMemory(chatId: string, userMessage: string | null, assistantReply: string): Promise<void> {
        if (!userMessage?.trim()) {
            return;
        }
        try {
            const existingMemory = (await this.chatMemoryRepository.find(chatId))?.memoryText ?? "";
            const mergedMemory = await this.memoryClient.merge(existingMemory, userMessage, assistantReply);
            if (mergedMemory !== existingMemory) {
                await this.chatMemoryRepository.persist(chatId, mergedMemory);
            }
        } catch (error) {
            this.logger.warn(`Failed to refresh long-term memory chat_id=${chatId} error=${(error as Error).message}`);
        }
    }
}
