import {Inject, Injectable} from "@nestjs/common";

import type {AppEnv} from "../config/env.js";
import {APP_ENV} from "../config/tokens.js";

@Injectable()
export class ChatRateLimiter {
    private readonly hits = new Map<string, number[]>();
    private readonly windowMs: number;
    private readonly maxMessages: number;

    public constructor(@Inject(APP_ENV) env: AppEnv) {
        this.windowMs = env.RATE_LIMIT_WINDOW_MS;
        this.maxMessages = env.RATE_LIMIT_MAX_MESSAGES;
    }

    public allow(chatId: string, now: number = Date.now()): boolean {
        const timestamps = this.hits.get(chatId) ?? [];
        const freshTimestamps = timestamps.filter(timestamp => now - timestamp < this.windowMs);

        if (freshTimestamps.length >= this.maxMessages) {
            this.hits.set(chatId, freshTimestamps);
            return false;
        }

        freshTimestamps.push(now);
        this.hits.set(chatId, freshTimestamps);

        return true;
    }
}
