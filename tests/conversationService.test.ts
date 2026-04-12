import {beforeEach, describe, expect, it, vi} from "vitest";

import type {AppEnv} from "../src/config/env.js";
import type {Logger, ProcessedUpdateRepository, ReplyClient, SessionRepository} from "../src/config/service.types.js";
import {ConversationService} from "../src/conversation/conversation.service.js";
import type {ChatSession, GenerateReplyResult} from "../src/conversation/conversation.types.js";

class InMemorySessionRepository implements SessionRepository {
    private readonly store = new Map<string, ChatSession>();

    public async getByChatId(chatId: string): Promise<ChatSession | null> {
        return this.store.get(chatId) ?? null;
    }

    public async upsert(session: ChatSession): Promise<void> {
        this.store.set(session.chatId, session);
    }

    public async delete(chatId: string): Promise<void> {
        this.store.delete(chatId);
    }
}

class InMemoryProcessedUpdateRepository implements ProcessedUpdateRepository {
    private readonly store = new Set<number>();

    public async hasProcessed(updateId: number): Promise<boolean> {
        return this.store.has(updateId);
    }

    public async markProcessed(updateId: number): Promise<void> {
        this.store.add(updateId);
    }
}

function createEnv(overrides?: Partial<AppEnv>): AppEnv {
    return {
        PORT: 3000,
        BASE_URL: "https://example.com",
        TELEGRAM_BOT_TOKEN: "token",
        TELEGRAM_WEBHOOK_SECRET: "secret",
        ALLOWED_TELEGRAM_USER_IDS: [],
        SQLITE_DB_PATH: "/tmp/test.db",
        SESSION_TTL_DAYS: 7,
        RATE_LIMIT_WINDOW_MS: 10_000,
        RATE_LIMIT_MAX_MESSAGES: 5,
        ...overrides,
    };
}

describe("ConversationService", () => {
    const logger: Logger = {
        info: vi.fn(),
        warn: vi.fn(),
        error: vi.fn(),
    };

    let sessionRepository: InMemorySessionRepository;
    let processedUpdateRepository: InMemoryProcessedUpdateRepository;
    let replyClient: ReplyClient;

    beforeEach(() => {
        vi.useRealTimers();
        sessionRepository = new InMemorySessionRepository();
        processedUpdateRepository = new InMemoryProcessedUpdateRepository();
        replyClient = {
            generateReply: vi.fn<ReplyClient["generateReply"]>(),
        };
    });

    it("passes through conversation state for an active session", async () => {
        await sessionRepository.upsert({
            chatId: "chat-1",
            conversationState: "state-old",
            updatedAt: Date.now(),
        });

        const generateReplyResult: GenerateReplyResult = {
            conversationState: "state-new",
            text: "reply",
        };

        vi.mocked(replyClient.generateReply).mockResolvedValue(generateReplyResult);

        const service = new ConversationService(sessionRepository, processedUpdateRepository, replyClient, logger, createEnv({SESSION_TTL_DAYS: 60_000 / (24 * 60 * 60 * 1000)}));

        const reply = await service.reply({
            chatId: "chat-1",
            imageFileId: null,
            messageId: 10,
            text: "hello",
            userId: "234392020",
            updateId: 100,
        });

        expect(reply).toBe("reply");
        expect(replyClient.generateReply).toHaveBeenCalledWith({
            chatId: "chat-1",
            text: "hello",
            conversationState: "state-old",
            imageFilePath: null,
        });
    });

    it("resets expired sessions before generating a reply", async () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date("2026-04-11T00:00:00.000Z"));

        await sessionRepository.upsert({
            chatId: "chat-1",
            conversationState: "state-old",
            updatedAt: Date.now() - 100_000,
        });

        vi.mocked(replyClient.generateReply).mockResolvedValue({
            conversationState: "state-new",
            text: "new reply",
        });

        const service = new ConversationService(sessionRepository, processedUpdateRepository, replyClient, logger, createEnv({SESSION_TTL_DAYS: 1_000 / (24 * 60 * 60 * 1000)}));

        await service.reply({
            chatId: "chat-1",
            imageFileId: null,
            messageId: 10,
            text: "hello",
            userId: "234392020",
            updateId: 100,
        });

        expect(replyClient.generateReply).toHaveBeenCalledWith({
            chatId: "chat-1",
            text: "hello",
            conversationState: null,
            imageFilePath: null,
        });
    });
});
