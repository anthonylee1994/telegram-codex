import {loadEnv} from "../config/env.js";
import {logger} from "../config/logger.js";
import {TelegramService} from "../telegram/telegramService.js";

async function main(): Promise<void> {
    const env = loadEnv();
    const telegramService = new TelegramService(env.TELEGRAM_BOT_TOKEN, logger);
    const webhookUrl = new URL("/telegram/webhook", env.BASE_URL).toString();

    await telegramService.setWebhook(webhookUrl, env.TELEGRAM_WEBHOOK_SECRET);
}

main().catch(error => {
    logger.error("Failed to set Telegram webhook", {
        error: error instanceof Error ? error.message : "unknown error",
    });
    process.exitCode = 1;
});
