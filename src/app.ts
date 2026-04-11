import express from "express";

import {getHealth} from "./routes/healthRoute.js";
import {createTelegramWebhookRoute} from "./routes/telegramRoute.js";
import type {TelegramWebhookHandler} from "./telegram/webhookHandler.js";
import type {Logger} from "./types/services.js";

export function createApp(telegramWebhookHandler: TelegramWebhookHandler, logger: Logger, telegramWebhookSecret: string): express.Express {
    const app = express();

    app.use(express.json({limit: "1mb"}));
    app.get("/health", getHealth);
    app.post("/telegram/webhook", createTelegramWebhookRoute(telegramWebhookHandler, logger, telegramWebhookSecret));

    return app;
}
