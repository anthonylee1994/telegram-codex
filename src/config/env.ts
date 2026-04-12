import fs from "node:fs";
import path from "node:path";
import dotenv from "dotenv";
import {z} from "zod";

dotenv.config();

const envSchema = z.object({
    PORT: z.coerce.number().int().positive().default(3000),
    BASE_URL: z.url(),
    TELEGRAM_BOT_TOKEN: z.string().min(1),
    TELEGRAM_WEBHOOK_SECRET: z.string().min(1),
    ALLOWED_TELEGRAM_USER_IDS: z
        .string()
        .optional()
        .transform(value =>
            value
                ? value
                      .split(",")
                      .map(item => item.trim())
                      .filter(Boolean)
                : []
        ),
    SQLITE_DB_PATH: z.string().min(1).default("./data/app.db"),
    SESSION_TTL_DAYS: z.coerce.number().int().positive().default(7),
    RATE_LIMIT_WINDOW_MS: z.coerce.number().int().positive().default(10000),
    RATE_LIMIT_MAX_MESSAGES: z.coerce.number().int().positive().default(5),
});

export type AppEnv = z.infer<typeof envSchema>;

export function loadEnv(): AppEnv {
    const parsed = envSchema.safeParse(process.env);

    if (!parsed.success) {
        throw new Error(`Invalid environment variables: ${parsed.error.issues.map(issue => `${issue.path.join(".")}: ${issue.message}`).join(", ")}`);
    }

    const env = parsed.data;
    const resolvedDbPath = path.resolve(env.SQLITE_DB_PATH);
    const dbDir = path.dirname(resolvedDbPath);

    fs.mkdirSync(dbDir, {recursive: true});

    return {
        ...env,
        SQLITE_DB_PATH: resolvedDbPath,
    };
}
