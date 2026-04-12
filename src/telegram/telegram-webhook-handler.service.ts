import {Inject, Injectable} from "@nestjs/common";
import fs from "node:fs/promises";
import path from "node:path";

import {createScopedLogger} from "../config/logger.js";
import {APP_ENV, LOGGER} from "../config/tokens.js";
import {ConversationService} from "../conversation/conversation.service.js";
import {ChatRateLimiter} from "../conversation/rate-limiter.service.js";
import {TelegramUpdateParser} from "./telegram-update-parser.service.js";
import {TelegramService} from "./telegram.service.js";
import type {AppEnv} from "../config/env.js";
import type {Logger} from "../config/service.types.js";

const UNSUPPORTED_MESSAGE = "而家只支援文字同圖片訊息，仲未支援檔案、語音。";
const RATE_LIMIT_MESSAGE = "你打得太快，等一陣再試。";
const GENERIC_ERROR_MESSAGE = "我而家有啲塞車，遲啲再試過。";
const UNAUTHORIZED_MESSAGE = "呢個 bot 暫時只限指定用戶使用。";
const NEW_SESSION_MESSAGE = "已經開咗個新 session，你可以重新開始。";
const START_MESSAGE = ["歡迎用 On99 Bot。", "", "直接 send 文字或者圖片畀我就得。", "想重新開過個 session，就打 `/new`。"].join("\n");

@Injectable()
export class TelegramWebhookHandler {
    private readonly allowedTelegramUserIds: string[];
    private readonly logger: Logger;

    public constructor(
        @Inject(ConversationService) private readonly conversationService: ConversationService,
        @Inject(TelegramService) private readonly telegramService: TelegramService,
        @Inject(ChatRateLimiter) private readonly rateLimiter: ChatRateLimiter,
        @Inject(TelegramUpdateParser) private readonly telegramUpdateParser: TelegramUpdateParser,
        @Inject(LOGGER) logger: Logger,
        @Inject(APP_ENV) env: AppEnv
    ) {
        this.logger = createScopedLogger(logger, TelegramWebhookHandler.name);
        this.allowedTelegramUserIds = env.ALLOWED_TELEGRAM_USER_IDS;
    }

    public async handle(update: unknown): Promise<void> {
        const message = this.telegramUpdateParser.parseIncomingTelegramMessage(update);

        if (!message || (!message.text && !message.imageFileId)) {
            await this.replyUnsupported(update);
            return;
        }

        const processedUpdate = await this.conversationService.getProcessedUpdate(message.updateId);

        if (processedUpdate?.sentAt) {
            this.logger.info("Ignored duplicate update", {
                updateId: message.updateId,
            });
            return;
        }

        if (processedUpdate?.replyText && processedUpdate.conversationState) {
            await this.telegramService.sendMessage(message.chatId, processedUpdate.replyText);
            await this.conversationService.persistConversationState(message.chatId, processedUpdate.conversationState);
            await this.conversationService.markProcessed(message.updateId, message.chatId, message.messageId);
            return;
        }

        if (this.allowedTelegramUserIds.length > 0 && !this.allowedTelegramUserIds.includes(message.userId)) {
            this.logger.warn("Rejected unauthorized Telegram user", {
                chatId: message.chatId,
                userId: message.userId,
            });
            await this.telegramService.sendMessage(message.chatId, UNAUTHORIZED_MESSAGE);
            await this.conversationService.markProcessed(message.updateId, message.chatId, message.messageId);
            return;
        }

        if (this.isNewSessionCommand(message.text)) {
            await this.conversationService.resetSession(message.chatId);
            await this.telegramService.sendMessage(message.chatId, NEW_SESSION_MESSAGE);
            await this.conversationService.markProcessed(message.updateId, message.chatId, message.messageId);
            return;
        }

        if (this.isStartCommand(message.text)) {
            await this.telegramService.sendMessage(message.chatId, START_MESSAGE);
            await this.conversationService.markProcessed(message.updateId, message.chatId, message.messageId);
            return;
        }

        if (!this.rateLimiter.allow(message.chatId)) {
            await this.telegramService.sendMessage(message.chatId, RATE_LIMIT_MESSAGE);
            await this.conversationService.markProcessed(message.updateId, message.chatId, message.messageId);
            return;
        }

        let hasPendingReply = false;

        try {
            const reply = await this.telegramService.withTypingStatus(message.chatId, async () => {
                const imageFilePath = message.imageFileId ? await this.telegramService.downloadFileToTemp(message.imageFileId) : null;

                try {
                    return await this.conversationService.generateReply({
                        ...message,
                        imageFilePath,
                    });
                } finally {
                    if (imageFilePath) {
                        await fs.rm(path.dirname(imageFilePath), {recursive: true, force: true});
                    }
                }
            });
            await this.conversationService.savePendingReply(message.updateId, message.chatId, message.messageId, reply);
            hasPendingReply = true;
            await this.telegramService.sendMessage(message.chatId, reply.text);
            await this.conversationService.persistConversationState(message.chatId, reply.conversationState);
            await this.conversationService.markProcessed(message.updateId, message.chatId, message.messageId);
        } catch (error) {
            this.logger.error("Failed to handle Telegram update", {
                error: error instanceof Error ? error.message : "unknown error",
                updateId: message.updateId,
                chatId: message.chatId,
            });

            if (hasPendingReply) {
                throw error;
            }

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

    private isNewSessionCommand(text: string): boolean {
        return /^\/new(?:@[\w_]+)?$/u.test(text);
    }

    private isStartCommand(text: string): boolean {
        return /^\/start(?:@[\w_]+)?$/u.test(text);
    }
}
