import fs from "node:fs/promises";
import path from "node:path";

import type {ConversationService} from "../conversation/conversationService.js";
import {ChatRateLimiter} from "../conversation/rateLimiter.js";
import type {Logger} from "../types/services.js";
import type {TelegramService} from "./telegramService.js";
import {parseIncomingTelegramMessage} from "./updateParser.js";

const UNSUPPORTED_MESSAGE = "而家只支援文字同圖片訊息，檔案、語音住先未得。";
const RATE_LIMIT_MESSAGE = "你打得太快，等一陣再試。";
const GENERIC_ERROR_MESSAGE = "我而家有啲塞車，遲啲再試過。";
const UNAUTHORIZED_MESSAGE = "呢個 bot 暫時只限指定用戶使用。";

export class TelegramWebhookHandler {
    public constructor(
        private readonly conversationService: ConversationService,
        private readonly telegramService: TelegramService,
        private readonly rateLimiter: ChatRateLimiter,
        private readonly logger: Logger,
        private readonly allowedTelegramUserIds: string[]
    ) {}

    public async handle(update: unknown): Promise<void> {
        const message = parseIncomingTelegramMessage(update);

        if (!message || (!message.text && !message.imageFileId)) {
            await this.replyUnsupported(update);
            return;
        }

        if (!this.allowedTelegramUserIds.includes(message.userId)) {
            this.logger.warn("Rejected unauthorized Telegram user", {
                chatId: message.chatId,
                userId: message.userId,
            });
            await this.telegramService.sendMessage(message.chatId, UNAUTHORIZED_MESSAGE);
            return;
        }

        const alreadyProcessed = await this.conversationService.hasProcessedUpdate(message.updateId);

        if (alreadyProcessed) {
            this.logger.info("Ignored duplicate update", {
                updateId: message.updateId,
            });
            return;
        }

        if (!this.rateLimiter.allow(message.chatId)) {
            await this.telegramService.sendMessage(message.chatId, RATE_LIMIT_MESSAGE);
            await this.conversationService.markProcessed(message.updateId, message.chatId, message.messageId);
            return;
        }

        try {
            const reply = await this.telegramService.withTypingStatus(message.chatId, async () => {
                const imageFilePath = message.imageFileId ? await this.telegramService.downloadFileToTemp(message.imageFileId) : null;

                try {
                    return await this.conversationService.reply({
                        ...message,
                        imageFilePath,
                    });
                } finally {
                    if (imageFilePath) {
                        await fs.rm(path.dirname(imageFilePath), {recursive: true, force: true});
                    }
                }
            });
            await this.telegramService.sendMessage(message.chatId, reply);
            await this.conversationService.markProcessed(message.updateId, message.chatId, message.messageId);
        } catch (error) {
            this.logger.error("Failed to handle Telegram update", {
                error: error instanceof Error ? error.message : "unknown error",
                updateId: message.updateId,
                chatId: message.chatId,
            });
            await this.telegramService.sendMessage(message.chatId, GENERIC_ERROR_MESSAGE);
        }
    }

    private async replyUnsupported(update: unknown): Promise<void> {
        if (!update || typeof update !== "object") {
            return;
        }

        const maybeChatId = (update as {message?: {chat?: {id?: number | string}}}).message?.chat?.id;

        if (maybeChatId === undefined) {
            return;
        }

        await this.telegramService.sendMessage(String(maybeChatId), UNSUPPORTED_MESSAGE);
    }
}
