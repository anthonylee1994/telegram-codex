import {beforeEach, describe, expect, it, vi} from "vitest";

import {ConversationService} from "../src/conversation/conversation.service.js";
import {ChatRateLimiter} from "../src/conversation/rate-limiter.service.js";
import {TelegramUpdateParser} from "../src/telegram/telegram-update-parser.service.js";
import {TelegramWebhookHandler} from "../src/telegram/telegram-webhook-handler.service.js";
import type {AppEnv} from "../src/config/env.js";
import type {Logger, ProcessedUpdateRepository, ReplyClient, SessionRepository} from "../src/config/service.types.js";
import type {ChatSession, ProcessedUpdate} from "../src/conversation/conversation.types.js";

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
    private readonly store = new Map<number, ProcessedUpdate>();

    public async getByUpdateId(updateId: number): Promise<ProcessedUpdate | null> {
        return this.store.get(updateId) ?? null;
    }

    public async savePendingReply(updateId: number, chatId: string, messageId: number, replyText: string, conversationState: string): Promise<void> {
        this.store.set(updateId, {
            chatId,
            conversationState,
            messageId,
            replyText,
            sentAt: null,
            updateId,
        });
    }

    public async markProcessed(updateId: number, chatId: string, messageId: number): Promise<void> {
        const existing = this.store.get(updateId);

        this.store.set(updateId, {
            chatId,
            conversationState: existing?.conversationState ?? null,
            messageId,
            replyText: existing?.replyText ?? null,
            sentAt: Date.now(),
            updateId,
        });
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

describe("TelegramWebhookHandler", () => {
    const logger: Logger = {
        info: vi.fn(),
        warn: vi.fn(),
        error: vi.fn(),
    };

    let sessionRepository: InMemorySessionRepository;
    let processedUpdateRepository: InMemoryProcessedUpdateRepository;
    let replyClient: ReplyClient;

    beforeEach(() => {
        sessionRepository = new InMemorySessionRepository();
        processedUpdateRepository = new InMemoryProcessedUpdateRepository();
        replyClient = {
            generateReply: vi.fn<ReplyClient["generateReply"]>().mockResolvedValue({
                conversationState: "state-1",
                text: "reply-1",
            }),
        };
    });

    it("re-sends a persisted pending reply without regenerating it", async () => {
        const conversationService = new ConversationService(sessionRepository, processedUpdateRepository, replyClient, logger, createEnv());
        const telegramService = {
            downloadFileToTemp: vi.fn(),
            sendMessage: vi.fn().mockRejectedValueOnce(new Error("telegram send failed")).mockResolvedValueOnce(undefined),
            withTypingStatus: vi.fn(async function withTypingStatus<T>(_chatId: string, action: () => Promise<T>): Promise<T> {
                return action();
            }),
        };
        const handler = new TelegramWebhookHandler(conversationService, telegramService as never, new ChatRateLimiter(createEnv()), new TelegramUpdateParser(), logger, createEnv());
        const update = {
            update_id: 1,
            message: {
                from: {
                    id: 234392020,
                },
                message_id: 2,
                text: "hello",
                chat: {
                    id: 3,
                },
            },
        };

        await expect(handler.handle(update)).rejects.toThrow("telegram send failed");
        await expect(handler.handle(update)).resolves.toBeUndefined();

        expect(replyClient.generateReply).toHaveBeenCalledTimes(1);
        expect(telegramService.sendMessage).toHaveBeenNthCalledWith(1, "3", "reply-1");
        expect(telegramService.sendMessage).toHaveBeenNthCalledWith(2, "3", "reply-1");
        await expect(sessionRepository.getByChatId("3")).resolves.toMatchObject({
            chatId: "3",
            conversationState: "state-1",
        });
        await expect(processedUpdateRepository.getByUpdateId(1)).resolves.toMatchObject({
            replyText: "reply-1",
            sentAt: expect.any(Number),
        });
    });
});
