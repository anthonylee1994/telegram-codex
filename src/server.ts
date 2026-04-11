import {createApp} from "./app.js";
import {CodexCliClient} from "./codex/codexCliClient.js";
import {loadEnv} from "./config/env.js";
import {logger} from "./config/logger.js";
import {ConversationService} from "./conversation/conversationService.js";
import {ChatRateLimiter} from "./conversation/rateLimiter.js";
import {SqliteStorage} from "./storage/sqlite.js";
import {TelegramService} from "./telegram/telegramService.js";
import {TelegramWebhookHandler} from "./telegram/webhookHandler.js";

const env = loadEnv();
const storage = new SqliteStorage(env.SQLITE_DB_PATH);
const replyClient = new CodexCliClient();
const conversationService = new ConversationService(storage, storage, replyClient, logger, env.SESSION_TTL_DAYS * 24 * 60 * 60 * 1000);
const telegramService = new TelegramService(env.TELEGRAM_BOT_TOKEN, logger);
const rateLimiter = new ChatRateLimiter(env.RATE_LIMIT_WINDOW_MS, env.RATE_LIMIT_MAX_MESSAGES);
const telegramWebhookHandler = new TelegramWebhookHandler(conversationService, telegramService, rateLimiter, logger, env.ALLOWED_TELEGRAM_USER_IDS);

const app = createApp(telegramWebhookHandler, logger, env.TELEGRAM_WEBHOOK_SECRET);

app.listen(env.PORT, () => {
    logger.info("Server started", {
        port: env.PORT,
    });
});
