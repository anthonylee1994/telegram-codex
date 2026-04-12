import {NestFactory} from "@nestjs/core";
import {AppModule} from "../app.module.js";
import {AppLogger, createScopedLogger} from "../config/logger.js";
import {APP_ENV} from "../config/tokens.js";
import {TelegramService} from "../telegram/telegram.service.js";
import type {AppEnv} from "../config/env.js";
import type {Logger} from "../config/service.types.js";

async function main(): Promise<void> {
    const app = await NestFactory.createApplicationContext(AppModule, {
        logger: false,
    });
    app.useLogger(app.get(AppLogger));
    const env = app.get<AppEnv>(APP_ENV);
    const logger = createScopedLogger(app.get<Logger>(AppLogger), "SetWebhookScript");
    const telegramService = app.get(TelegramService);
    const webhookUrl = new URL("/telegram/webhook", env.BASE_URL).toString();

    try {
        await telegramService.setWebhook(webhookUrl, env.TELEGRAM_WEBHOOK_SECRET);
        logger.info("Set Telegram webhook", {webhookUrl});
    } finally {
        await app.close();
    }
}

main().catch(error => {
    console.error(
        JSON.stringify({
            level: "ERROR",
            message: "Failed to set Telegram webhook",
            context: {
                error: error instanceof Error ? error.message : "unknown error",
            },
        })
    );
    process.exitCode = 1;
});
