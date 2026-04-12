import {Inject, Injectable} from "@nestjs/common";
import {createScopedLogger} from "../config/logger.js";
import {APP_ENV, LOGGER, PROCESSED_UPDATE_REPOSITORY, REPLY_CLIENT, SESSION_REPOSITORY} from "../config/tokens.js";
import {SYSTEM_PROMPT} from "./prompts.js";
import type {AppEnv} from "../config/env.js";
import type {Logger, ProcessedUpdateRepository, ReplyClient, SessionRepository} from "../config/service.types.js";
import type {ChatSession, GenerateReplyResult, IncomingTelegramMessage, ProcessedUpdate} from "./conversation.types.js";

@Injectable()
export class ConversationService {
    private readonly sessionTtlMs: number;
    private readonly logger: Logger;

    public constructor(
        @Inject(SESSION_REPOSITORY) private readonly sessionRepository: SessionRepository,
        @Inject(PROCESSED_UPDATE_REPOSITORY) private readonly processedUpdateRepository: ProcessedUpdateRepository,
        @Inject(REPLY_CLIENT) private readonly replyClient: ReplyClient,
        @Inject(LOGGER) logger: Logger,
        @Inject(APP_ENV) env: AppEnv
    ) {
        this.logger = createScopedLogger(logger, ConversationService.name);
        this.sessionTtlMs = env.SESSION_TTL_DAYS * 24 * 60 * 60 * 1000;
    }

    public async getProcessedUpdate(updateId: number): Promise<ProcessedUpdate | null> {
        return this.processedUpdateRepository.getByUpdateId(updateId);
    }

    public async markProcessed(updateId: number, chatId: string, messageId: number): Promise<void> {
        await this.processedUpdateRepository.markProcessed(updateId, chatId, messageId);
    }

    public async savePendingReply(updateId: number, chatId: string, messageId: number, result: GenerateReplyResult): Promise<void> {
        await this.processedUpdateRepository.savePendingReply(updateId, chatId, messageId, result.text, result.conversationState);
    }

    public async persistConversationState(chatId: string, conversationState: string): Promise<void> {
        await this.sessionRepository.upsert({
            chatId,
            conversationState,
            updatedAt: Date.now(),
        });
    }

    public async resetSession(chatId: string): Promise<void> {
        await this.sessionRepository.delete(chatId);
        this.logger.info("Reset chat session", {chatId});
    }

    public async generateReply(message: IncomingTelegramMessage): Promise<GenerateReplyResult> {
        const session = await this.loadActiveSession(message.chatId);
        const result = await this.replyClient.generateReply({
            chatId: message.chatId,
            text: message.text,
            conversationState: session?.conversationState ?? null,
            imageFilePath: message.imageFilePath ?? null,
        });

        this.logger.info("Generated assistant reply", {
            chatId: message.chatId,
        });

        return result;
    }

    public getSystemPrompt(): string {
        return SYSTEM_PROMPT;
    }

    private async loadActiveSession(chatId: string): Promise<ChatSession | null> {
        const session = await this.sessionRepository.getByChatId(chatId);

        if (!session) {
            return null;
        }

        const isExpired = Date.now() - session.updatedAt > this.sessionTtlMs;

        if (!isExpired) {
            return session;
        }

        await this.sessionRepository.delete(chatId);
        this.logger.info("Reset expired session", {chatId});

        return null;
    }
}
