import type {Request, Response} from "express";

import type {TelegramWebhookHandler} from "../telegram/webhookHandler.js";
import type {Logger} from "../types/services.js";

export function createTelegramWebhookRoute(webhookHandler: TelegramWebhookHandler, logger: Logger, expectedSecret: string): (request: Request, response: Response) => Promise<void> {
    return async function telegramWebhookRoute(request: Request, response: Response): Promise<void> {
        const secret = getTelegramWebhookSecret(request);

        if (secret !== expectedSecret) {
            logger.warn("Rejected Telegram webhook request with invalid secret", {
                hasSecretHeader: secret !== undefined,
            });
            response.status(401).json({ok: false});
            return;
        }

        try {
            await webhookHandler.handle(request.body);
            response.status(200).json({ok: true});
        } catch (error) {
            logger.error("Unexpected webhook route failure", {
                error: error instanceof Error ? error.message : "unknown error",
            });
            response.status(200).json({ok: true});
        }
    };
}

function getTelegramWebhookSecret(request: Request): string | undefined {
    const headerValue = request.header("x-telegram-bot-api-secret-token") ?? request.headers["x-telegram-bot-api-secret-token"];

    if (Array.isArray(headerValue)) {
        return headerValue[0];
    }

    return headerValue;
}
