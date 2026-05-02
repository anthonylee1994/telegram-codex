import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import {Injectable, Logger} from "@nestjs/common";
import {AppConfigService} from "../config/app-config.service";
import {TelegramBotCommand, TelegramGateway, TELEGRAM_CONSTANTS} from "./telegram.types";
import {TelegramMessageFormatterService} from "./telegram-message-formatter.service";
import {TypingStatusService} from "./typing-status.service";

interface TelegramApiResponse<T> {
    ok?: boolean;
    result?: T;
}

interface TelegramFileResult {
    file_path?: string;
}

@Injectable()
export class TelegramApiService implements TelegramGateway {
    private readonly logger = new Logger(TelegramApiService.name);

    public constructor(
        private readonly config: AppConfigService,
        private readonly formatter: TelegramMessageFormatterService,
        private readonly typingStatus: TypingStatusService
    ) {}

    public async downloadFileToTemp(fileId: string): Promise<string> {
        const file = await this.getFile(fileId);
        if (!file.file_path) {
            throw new Error("Telegram getFile did not include a file path");
        }
        const tempDir = await fs.mkdtemp(path.join(os.tmpdir(), "telegram-codex-file-"));
        const outputPath = path.join(tempDir, path.basename(file.file_path));
        const response = await fetch(`${TELEGRAM_CONSTANTS.fileApiBase}${this.config.telegramBotToken}/${file.file_path}`);
        if (!response.ok) {
            throw new Error(`Failed to download Telegram file: HTTP ${response.status}`);
        }
        await fs.writeFile(outputPath, Buffer.from(await response.arrayBuffer()));
        return outputPath;
    }

    public async sendMessage(chatId: string, text: string | null | undefined, suggestedReplies: string[], removeKeyboard: boolean): Promise<void> {
        const normalized = this.formatter.normalizeReply(text, suggestedReplies);
        const replyMarkup = this.formatter.buildReplyMarkup(normalized.suggestedReplies, removeKeyboard);
        await this.postForm("sendMessage", {
            chat_id: chatId,
            text: this.formatter.formatForTelegram(normalized.text),
            parse_mode: "HTML",
            ...(replyMarkup ? {reply_markup: JSON.stringify(replyMarkup)} : {}),
        });
    }

    public async withTypingStatus<T>(chatId: string, action: () => Promise<T>): Promise<T> {
        return this.typingStatus.withTypingStatus(chatId, id => this.sendChatAction(id, "typing"), action);
    }

    public async setWebhook(url: string, secretToken: string): Promise<void> {
        await this.postForm("setWebhook", {url, secret_token: secretToken});
        this.logger.log(`Telegram webhook configured url=${url}`);
    }

    public async setMyCommands(commands: TelegramBotCommand[]): Promise<void> {
        await this.postForm("setMyCommands", {commands: JSON.stringify(commands)});
        this.logger.log(`Telegram commands updated count=${commands.length}`);
    }

    private async sendChatAction(chatId: string, action: string): Promise<void> {
        await this.postForm("sendChatAction", {chat_id: chatId, action});
    }

    private async getFile(fileId: string): Promise<TelegramFileResult> {
        const url = `${TELEGRAM_CONSTANTS.apiBase}${this.config.telegramBotToken}/getFile?file_id=${encodeURIComponent(fileId)}`;
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`Failed to call Telegram getFile: HTTP ${response.status}`);
        }
        const payload = (await response.json()) as TelegramApiResponse<TelegramFileResult>;
        if (!payload.ok || !payload.result) {
            throw new Error("Failed to call Telegram getFile: invalid response");
        }
        return payload.result;
    }

    private async postForm(methodName: string, params: Record<string, string>): Promise<void> {
        const response = await fetch(`${TELEGRAM_CONSTANTS.apiBase}${this.config.telegramBotToken}/${methodName}`, {
            method: "POST",
            headers: {"Content-Type": "application/x-www-form-urlencoded"},
            body: new URLSearchParams(params).toString(),
        });
        if (!response.ok) {
            throw new Error(`Failed to call Telegram ${methodName}: HTTP ${response.status}`);
        }
        const payload = (await response.json()) as TelegramApiResponse<unknown>;
        if (!payload.ok) {
            throw new Error(`Failed to call Telegram ${methodName}: invalid response`);
        }
    }
}
