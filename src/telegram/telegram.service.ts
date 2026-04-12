import {Inject, Injectable} from "@nestjs/common";
import {Bot} from "grammy";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";

import type {AppEnv} from "../config/env.js";
import {createScopedLogger} from "../config/logger.js";
import type {Logger} from "../config/service.types.js";
import {APP_ENV, LOGGER} from "../config/tokens.js";

const TYPING_INTERVAL_MS = 4_000;

@Injectable()
export class TelegramService {
    private readonly bot: Bot;
    private readonly logger: Logger;

    public constructor(@Inject(APP_ENV) env: AppEnv, @Inject(LOGGER) logger: Logger) {
        this.bot = new Bot(env.TELEGRAM_BOT_TOKEN);
        this.logger = createScopedLogger(logger, TelegramService.name);
    }

    public async downloadFileToTemp(fileId: string): Promise<string> {
        const file = await this.bot.api.getFile(fileId);

        if (!file.file_path) {
            throw new Error("Telegram file did not include a file path.");
        }

        const tempDir = await fs.mkdtemp(path.join(os.tmpdir(), "telegram-codex-file-"));
        const fileName = path.basename(file.file_path);
        const outputPath = path.join(tempDir, fileName);
        const fileUrl = `https://api.telegram.org/file/bot${this.bot.token}/${file.file_path}`;
        const response = await fetch(fileUrl);

        if (!response.ok) {
            throw new Error(`Failed to download Telegram file: ${response.status} ${response.statusText}`);
        }

        const arrayBuffer = await response.arrayBuffer();

        await fs.writeFile(outputPath, new Uint8Array(arrayBuffer));

        return outputPath;
    }

    public async sendMessage(chatId: string, text: string): Promise<void> {
        await this.bot.api.sendMessage(chatId, formatTelegramMessage(text), {
            parse_mode: "HTML",
        });
    }

    public async withTypingStatus<T>(chatId: string, action: () => Promise<T>): Promise<T> {
        let timer: NodeJS.Timeout | undefined;

        try {
            await this.bot.api.sendChatAction(chatId, "typing");
            timer = setInterval(() => {
                void this.bot.api.sendChatAction(chatId, "typing").catch(() => undefined);
            }, TYPING_INTERVAL_MS);

            return await action();
        } finally {
            if (timer) {
                clearInterval(timer);
            }
        }
    }

    public async setWebhook(url: string, secretToken: string): Promise<void> {
        await this.bot.api.setWebhook(url, {
            secret_token: secretToken,
            allowed_updates: ["message"],
        });

        this.logger.info("Telegram webhook configured", {url});
    }
}

function formatTelegramMessage(text: string): string {
    const placeholders = new Map<string, string>();
    let placeholderIndex = 0;
    let formatted = escapeHtml(text);

    formatted = formatted.replace(/```([\s\S]*?)```/g, function replaceCodeBlock(_match: string, code: string): string {
        const key = createPlaceholderKey(placeholderIndex);
        placeholderIndex += 1;
        placeholders.set(key, `<pre><code>${code.trim()}</code></pre>`);
        return key;
    });

    formatted = formatted.replace(/`([^`\n]+)`/g, function replaceInlineCode(_match: string, code: string): string {
        return `<code>${code}</code>`;
    });

    formatted = formatted.replace(/\*\*([^*\n]+)\*\*/g, "<b>$1</b>");
    formatted = formatted.replace(/(^|\n)#{1,6}\s+([^\n]+)/g, "$1<b>$2</b>");

    for (const [key, value] of placeholders.entries()) {
        formatted = formatted.replace(key, value);
    }

    return formatted;
}

function escapeHtml(text: string): string {
    return text.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
}

function createPlaceholderKey(index: number): string {
    return `TELEGRAM_CODE_BLOCK_${index}__`;
}
