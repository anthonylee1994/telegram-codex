import type {ChatSession, IncomingTelegramMessage} from "../types/conversation.js";
import type {Logger, ProcessedUpdateRepository, ReplyClient, SessionRepository} from "../types/services.js";

import {SYSTEM_PROMPT} from "./prompts.js";

export class ConversationService {
    public constructor(
        private readonly sessionRepository: SessionRepository,
        private readonly processedUpdateRepository: ProcessedUpdateRepository,
        private readonly replyClient: ReplyClient,
        private readonly logger: Logger,
        private readonly sessionTtlMs: number
    ) {}

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
