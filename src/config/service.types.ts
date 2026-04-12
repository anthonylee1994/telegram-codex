import type {ChatSession, GenerateReplyInput, GenerateReplyResult, ProcessedUpdate} from "../conversation/conversation.types.js";

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
    getByUpdateId(updateId: number): Promise<ProcessedUpdate | null>;
    savePendingReply(updateId: number, chatId: string, messageId: number, replyText: string, conversationState: string): Promise<void>;
    markProcessed(updateId: number, chatId: string, messageId: number): Promise<void>;
}

export interface ReplyClient {
    generateReply(input: GenerateReplyInput): Promise<GenerateReplyResult>;
}
