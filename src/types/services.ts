import type {ChatSession, GenerateReplyInput, GenerateReplyResult} from "./conversation.js";

export interface Logger {
    info(message: string, context?: Record<string, unknown>): void;
    warn(message: string, context?: Record<string, unknown>): void;
    error(message: string, context?: Record<string, unknown>): void;
}

export interface SessionRepository {
    getByChatId(chatId: string): Promise<ChatSession | null>;
    upsert(session: ChatSession): Promise<void>;
    delete(chatId: string): Promise<void>;
}

export interface ProcessedUpdateRepository {
    hasProcessed(updateId: number): Promise<boolean>;
    markProcessed(updateId: number, chatId: string, messageId: number): Promise<void>;
}

export interface ReplyClient {
    generateReply(input: GenerateReplyInput): Promise<GenerateReplyResult>;
}
