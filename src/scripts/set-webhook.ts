import {NestFactory} from "@nestjs/core";
import {AppModule} from "../app.module.js";
import {APP_ENV} from "../config/tokens.js";
import {TelegramService} from "../telegram/telegram.service.js";
import type {AppEnv} from "../config/env.js";

async function main(): Promise<void> {
    const app = await NestFactory.createApplicationContext(AppModule, {
        logger: false,
    });
    const env = app.get<AppEnv>(APP_ENV);
    const telegramService = app.get(TelegramService);
    const webhookUrl = new URL("/telegram/webhook", env.BASE_URL).toString();

    try {
        await telegramService.setWebhook(webhookUrl, env.TELEGRAM_WEBHOOK_SECRET);
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
