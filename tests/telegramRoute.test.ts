import {describe, expect, it, vi} from "vitest";

import {TelegramController} from "../src/telegram/telegram.controller.js";
import {TelegramWebhookHandler} from "../src/telegram/telegram-webhook-handler.service.js";
import type {Logger} from "../src/config/service.types.js";

interface MockResponse {
    req: {
        body: unknown;
    };
    status(code: number): MockResponse;
    json(payload: unknown): MockResponse;
    statusCode?: number;
    body?: unknown;
}

function createMockResponse(body: unknown): MockResponse {
    return {
        req: {
            body,
        },
        statusCode: undefined,
        body: undefined,
        status(code: number) {
            this.statusCode = code;
            return this;
        },
        json(payload: unknown) {
            this.body = payload;
            return this;
        },
    };
}

describe("TelegramController", () => {
    const logger: Logger = {
        info: vi.fn(),
        warn: vi.fn(),
        error: vi.fn(),
    };

    it("returns 401 for invalid webhook secret", async () => {
        const webhookHandler = {
            handle: vi.fn(),
        };
        const controller = new TelegramController(webhookHandler as unknown as TelegramWebhookHandler, logger, {
            TELEGRAM_WEBHOOK_SECRET: "expected-secret",
        } as never);
        const response = createMockResponse({});

        await controller.handleWebhook("wrong-secret", response as never);

        expect(response.statusCode).toBe(401);
        expect(response.body).toEqual({ok: false});
        expect(webhookHandler.handle).not.toHaveBeenCalled();
    });

    it("processes valid text updates", async () => {
        const update = {
            update_id: 1,
            message: {
                message_id: 2,
                text: "hello",
                chat: {
                    id: 3,
                },
            },
        };
        const webhookHandler = {
            handle: vi.fn().mockResolvedValue(undefined),
        };
        const controller = new TelegramController(webhookHandler as unknown as TelegramWebhookHandler, logger, {
            TELEGRAM_WEBHOOK_SECRET: "expected-secret",
        } as never);
        const response = createMockResponse(update);

        await controller.handleWebhook("expected-secret", response as never);

        expect(response.statusCode).toBe(200);
        expect(response.body).toEqual({ok: true});
        expect(webhookHandler.handle).toHaveBeenCalledWith(update);
    });

    it("returns 500 when webhook handling throws", async () => {
        const webhookHandler = {
            handle: vi.fn().mockRejectedValue(new Error("boom")),
        };
        const controller = new TelegramController(webhookHandler as unknown as TelegramWebhookHandler, logger, {
            TELEGRAM_WEBHOOK_SECRET: "expected-secret",
        } as never);
        const response = createMockResponse({});

        await controller.handleWebhook("expected-secret", response as never);

        expect(response.statusCode).toBe(500);
        expect(response.body).toEqual({ok: false});
    });
});
