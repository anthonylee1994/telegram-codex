import {Inject, Injectable} from "@nestjs/common";

import type {AppEnv} from "../config/env.js";
import {createScopedLogger} from "../config/logger.js";
import type {Logger, ProcessedUpdateRepository, ReplyClient, SessionRepository} from "../config/service.types.js";
import {APP_ENV, LOGGER, PROCESSED_UPDATE_REPOSITORY, REPLY_CLIENT, SESSION_REPOSITORY} from "../config/tokens.js";
import type {ChatSession, IncomingTelegramMessage} from "./conversation.types.js";

import {SYSTEM_PROMPT} from "./prompts.js";

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

    public async hasProcessedUpdate(updateId: number): Promise<boolean> {
        return this.processedUpdateRepository.hasProcessed(updateId);
    }

    public async markProcessed(updateId: number, chatId: string, messageId: number): Promise<void> {
        await this.processedUpdateRepository.markProcessed(updateId, chatId, messageId);
    }

    public async reply(message: IncomingTelegramMessage): Promise<string> {
        const session = await this.loadActiveSession(message.chatId);
        const result = await this.replyClient.generateReply({
            chatId: message.chatId,
            text: message.text,
            conversationState: session?.conversationState ?? null,
            imageFilePath: message.imageFilePath ?? null,
        });

        await this.sessionRepository.upsert({
            chatId: message.chatId,
            conversationState: result.conversationState,
            updatedAt: Date.now(),
        });

        this.logger.info("Generated assistant reply", {
            chatId: message.chatId,
        });

        return result.text;
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
