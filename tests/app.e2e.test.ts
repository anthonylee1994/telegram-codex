import {Test} from "@nestjs/testing";
import {afterEach, describe, expect, it, vi} from "vitest";

import {AppModule} from "../src/app.module.js";
import {APP_ENV, LOGGER} from "../src/config/tokens.js";
import {TelegramWebhookHandler} from "../src/telegram/telegram-webhook-handler.service.js";
import type {INestApplication} from "@nestjs/common";
import type {AppEnv} from "../src/config/env.js";
import type {Logger} from "../src/config/service.types.js";

function createEnv(overrides?: Partial<AppEnv>): AppEnv {
    return {
        PORT: 3000,
        BASE_URL: "https://example.com",
        TELEGRAM_BOT_TOKEN: "token",
        TELEGRAM_WEBHOOK_SECRET: "expected-secret",
        ALLOWED_TELEGRAM_USER_IDS: [],
        SQLITE_DB_PATH: "/tmp/test.db",
        SESSION_TTL_DAYS: 7,
        RATE_LIMIT_WINDOW_MS: 10_000,
        RATE_LIMIT_MAX_MESSAGES: 5,
        ...overrides,
    };
}

describe("AppModule e2e", () => {
    let app: INestApplication | undefined;

    afterEach(async () => {
        await app?.close();
        app = undefined;
    });

    async function invokeHttpRequest(input: {body?: unknown; headers?: Record<string, string>; method: string; url: string}): Promise<{body: unknown; statusCode: number}> {
        const httpApp = app!.getHttpAdapter().getInstance() as {
            handle(request: MockRequest, response: MockResponse, callback: (error?: unknown) => void): void;
        };

        return await new Promise<{body: unknown; statusCode: number}>(function handleRequest(resolve, reject) {
            const request = createMockRequest(input);
            const response = createMockResponse(resolve);

            httpApp.handle(request, response, reject);
        });
    }

    it("serves GET /health", async () => {
        const moduleRef = await Test.createTestingModule({
            imports: [AppModule],
        })
            .overrideProvider(APP_ENV)
            .useValue(createEnv())
            .overrideProvider(LOGGER)
            .useValue({
                info: vi.fn(),
                warn: vi.fn(),
                error: vi.fn(),
            } satisfies Logger)
            .compile();

        app = moduleRef.createNestApplication();
        await app.init();

        await expect(invokeHttpRequest({method: "GET", url: "/health"})).resolves.toEqual({
            statusCode: 200,
            body: {ok: true},
        });
    });

    it("processes POST /telegram/webhook with valid secret", async () => {
        const webhookHandler = {
            handle: vi.fn().mockResolvedValue(undefined),
        };
        const moduleRef = await Test.createTestingModule({
            imports: [AppModule],
        })
            .overrideProvider(APP_ENV)
            .useValue(createEnv())
            .overrideProvider(LOGGER)
            .useValue({
                info: vi.fn(),
                warn: vi.fn(),
                error: vi.fn(),
            } satisfies Logger)
            .overrideProvider(TelegramWebhookHandler)
            .useValue(webhookHandler)
            .compile();

        app = moduleRef.createNestApplication();
        await app.init();

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

        await expect(
            invokeHttpRequest({
                method: "POST",
                url: "/telegram/webhook",
                headers: {
                    "x-telegram-bot-api-secret-token": "expected-secret",
                },
                body: update,
            })
        ).resolves.toEqual({
            statusCode: 200,
            body: {ok: true},
        });

        expect(webhookHandler.handle).toHaveBeenCalledWith(update);
    });

    it("rejects POST /telegram/webhook with invalid secret", async () => {
        const webhookHandler = {
            handle: vi.fn(),
        };
        const logger: Logger = {
            info: vi.fn(),
            warn: vi.fn(),
            error: vi.fn(),
        };
        const moduleRef = await Test.createTestingModule({
            imports: [AppModule],
        })
            .overrideProvider(APP_ENV)
            .useValue(createEnv())
            .overrideProvider(LOGGER)
            .useValue(logger)
            .overrideProvider(TelegramWebhookHandler)
            .useValue(webhookHandler)
            .compile();

        app = moduleRef.createNestApplication();
        await app.init();

        await expect(
            invokeHttpRequest({
                method: "POST",
                url: "/telegram/webhook",
                headers: {
                    "x-telegram-bot-api-secret-token": "wrong-secret",
                },
                body: {},
            })
        ).resolves.toEqual({
            statusCode: 401,
            body: {ok: false},
        });

        expect(webhookHandler.handle).not.toHaveBeenCalled();
        expect(logger.warn).toHaveBeenCalled();
    });
});

interface MockRequest {
    body?: unknown;
    headers: Record<string, string>;
    method: string;
    url: string;
}

interface MockResponse {
    body?: unknown;
    end(chunk?: unknown): MockResponse;
    getHeader(name: string): string | undefined;
    headersSent: boolean;
    json(payload: unknown): MockResponse;
    removeHeader(name: string): MockResponse;
    setHeader(name: string, value: string): MockResponse;
    status(code: number): MockResponse;
    statusCode: number;
}

function createMockRequest(input: {body?: unknown; headers?: Record<string, string>; method: string; url: string}): MockRequest {
    return {
        method: input.method,
        url: input.url,
        headers: input.headers ?? {},
        body: input.body,
    };
}

function createMockResponse(resolve: (value: {body: unknown; statusCode: number}) => void): MockResponse {
    const headers = new Map<string, string>();

    return {
        statusCode: 200,
        headersSent: false,
        status(code: number) {
            this.statusCode = code;
            return this;
        },
        setHeader(name: string, value: string) {
            headers.set(name.toLowerCase(), value);
            return this;
        },
        getHeader(name: string) {
            return headers.get(name.toLowerCase());
        },
        removeHeader(name: string) {
            headers.delete(name.toLowerCase());
            return this;
        },
        json(payload: unknown) {
            this.body = payload;
            this.headersSent = true;
            resolve({
                statusCode: this.statusCode,
                body: payload,
            });
            return this;
        },
        end(chunk?: unknown) {
            this.headersSent = true;
            resolve({
                statusCode: this.statusCode,
                body: chunk,
            });
            return this;
        },
    };
}
