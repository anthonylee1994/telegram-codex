import {Controller, Headers, Inject, Post, Res} from "@nestjs/common";
import type {Response} from "express";

import type {AppEnv} from "../config/env.js";
import {createScopedLogger} from "../config/logger.js";
import type {Logger} from "../config/service.types.js";
import {APP_ENV, LOGGER} from "../config/tokens.js";
import {TelegramWebhookHandler} from "./telegram-webhook-handler.service.js";

@Controller("telegram")
export class TelegramController {
    private readonly logger: Logger;

    public constructor(
        @Inject(TelegramWebhookHandler) private readonly webhookHandler: TelegramWebhookHandler,
        @Inject(LOGGER) logger: Logger,
        @Inject(APP_ENV) private readonly env: AppEnv
    ) {
        this.logger = createScopedLogger(logger, TelegramController.name);
    }

    @Post("webhook")
    public async handleWebhook(@Headers("x-telegram-bot-api-secret-token") secret: string | string[] | undefined, @Res() response: Response): Promise<void> {
        const headerSecret = Array.isArray(secret) ? secret[0] : secret;

        if (headerSecret !== this.env.TELEGRAM_WEBHOOK_SECRET) {
            this.logger.warn("Rejected Telegram webhook request with invalid secret", {
                hasSecretHeader: headerSecret !== undefined,
            });
            response.status(401).json({ok: false});
            return;
        }

        try {
            await this.webhookHandler.handle(response.req.body);
            response.status(200).json({ok: true});
        } catch (error) {
            this.logger.error("Unexpected webhook route failure", {
                error: error instanceof Error ? error.message : "unknown error",
            });
            response.status(200).json({ok: true});
        }
    }
}
