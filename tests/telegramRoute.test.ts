import {describe, expect, it, vi} from "vitest";

import {createTelegramWebhookRoute} from "../src/routes/telegramRoute.js";
import type {Logger} from "../src/types/services.js";

interface MockRequest {
    body: unknown;
    header(name: string): string | undefined;
}

interface MockResponse {
    status(code: number): MockResponse;
    json(payload: unknown): MockResponse;
    statusCode?: number;
    body?: unknown;
}

function createMockResponse(): MockResponse {
    return {
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

describe("telegram webhook route", () => {
    const logger: Logger = {
        info: vi.fn(),
        warn: vi.fn(),
        error: vi.fn(),
    };

    it("returns 401 for invalid webhook secret", async () => {
        const webhookHandler = {
            handle: vi.fn(),
        };

        const route = createTelegramWebhookRoute(webhookHandler as never, logger, "expected-secret");
        const response = createMockResponse();

        const request = {
            body: {},
            header: vi.fn().mockReturnValue("wrong-secret"),
        } satisfies MockRequest;

        await route(request as never, response as never);

        expect(response.statusCode).toBe(401);
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

        const route = createTelegramWebhookRoute(webhookHandler as never, logger, "expected-secret");
        const response = createMockResponse();

        const request = {
            body: update,
            header: vi.fn().mockReturnValue("expected-secret"),
        } satisfies MockRequest;

        await route(request as never, response as never);

        expect(response.statusCode).toBe(200);
        expect(webhookHandler.handle).toHaveBeenCalledWith(update);
    });
});
