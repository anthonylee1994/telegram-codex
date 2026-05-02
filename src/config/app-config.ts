import * as path from "node:path";

export interface AppConfig {
    port: number;
    baseUrl: string;
    telegramBotToken: string;
    telegramWebhookSecret: string;
    allowedTelegramUserIds: string[];
    sqliteDbPath: string;
    codexExecTimeoutSeconds: number;
    maxMediaGroupImages: number;
    sessionTtlDays: number;
    mediaGroupWaitMs: number;
    rateLimitWindowMs: number;
    rateLimitMaxMessages: number;
    codexSandboxMode: string;
}

function numberValue(name: string, fallback: number): number {
    const raw = process.env[name];
    if (raw === undefined || raw.trim() === "") {
        return fallback;
    }
    const value = Number(raw);
    if (!Number.isFinite(value)) {
        throw new Error(`${name} must be a number`);
    }
    return value;
}

function requiredString(name: string): string {
    const value = process.env[name]?.trim();
    if (!value) {
        throw new Error(`${name} is required`);
    }
    return value;
}

export function appConfig(): AppConfig {
    return {
        port: numberValue("PORT", 3000),
        baseUrl: requiredString("BASE_URL").replace(/\/+$/, ""),
        telegramBotToken: requiredString("TELEGRAM_BOT_TOKEN"),
        telegramWebhookSecret: requiredString("TELEGRAM_WEBHOOK_SECRET"),
        allowedTelegramUserIds: (process.env.ALLOWED_TELEGRAM_USER_IDS ?? "")
            .split(",")
            .map(value => value.trim())
            .filter(Boolean),
        sqliteDbPath: path.normalize(process.env.SQLITE_DB_PATH || "./data/app.db"),
        codexExecTimeoutSeconds: numberValue("CODEX_EXEC_TIMEOUT_SECONDS", 300),
        maxMediaGroupImages: numberValue("MAX_MEDIA_GROUP_IMAGES", 10),
        sessionTtlDays: numberValue("SESSION_TTL_DAYS", 7),
        mediaGroupWaitMs: numberValue("MEDIA_GROUP_WAIT_MS", 1200),
        rateLimitWindowMs: numberValue("RATE_LIMIT_WINDOW_MS", 10000),
        rateLimitMaxMessages: numberValue("RATE_LIMIT_MAX_MESSAGES", 5),
        codexSandboxMode: process.env.CODEX_SANDBOX_MODE || "danger-full-access",
    };
}
