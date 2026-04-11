import {beforeEach, describe, expect, it, vi} from "vitest";

import {ConversationService} from "../src/conversation/conversationService.js";
import type {ChatSession, GenerateReplyResult} from "../src/types/conversation.js";
import type {Logger, ProcessedUpdateRepository, ReplyClient, SessionRepository} from "../src/types/services.js";

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

        const service = new ConversationService(sessionRepository, processedUpdateRepository, replyClient, logger, 60_000);

        const reply = await service.reply({
            chatId: "chat-1",
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

        const service = new ConversationService(sessionRepository, processedUpdateRepository, replyClient, logger, 1_000);

        await service.reply({
            chatId: "chat-1",
            messageId: 10,
            text: "hello",
            userId: "234392020",
            updateId: 100,
        });

        expect(replyClient.generateReply).toHaveBeenCalledWith({
            chatId: "chat-1",
            text: "hello",
            conversationState: null,
        });
    });
});
