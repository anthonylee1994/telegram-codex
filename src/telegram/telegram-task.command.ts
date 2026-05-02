import {Inject} from "@nestjs/common";
import {Command, CommandRunner} from "nest-commander";
import {AppConfigService} from "../config/app-config.service";
import {TelegramGateway, TELEGRAM_GATEWAY} from "./telegram.types";

const BOT_COMMANDS = [
    {command: "status", description: "Bot 狀態"},
    {command: "session", description: "目前 session 狀態"},
    {command: "memory", description: "長期記憶狀態"},
    {command: "forget", description: "清除長期記憶"},
    {command: "compact", description: "壓縮目前對話 context"},
    {command: "new", description: "新 session"},
    {command: "help", description: "使用說明"},
];

@Command({name: "telegram:set-webhook", description: "Configure Telegram webhook"})
export class TelegramSetWebhookCommand extends CommandRunner {
    public constructor(
        private readonly config: AppConfigService,
        @Inject(TELEGRAM_GATEWAY) private readonly telegramClient: TelegramGateway
    ) {
        super();
    }

    public async run(): Promise<void> {
        await this.telegramClient.setWebhook(`${this.config.baseUrl}/telegram/webhook`, this.config.telegramWebhookSecret);
    }
}

@Command({name: "telegram:update-commands", description: "Update Telegram bot commands"})
export class TelegramUpdateCommandsCommand extends CommandRunner {
    public constructor(@Inject(TELEGRAM_GATEWAY) private readonly telegramClient: TelegramGateway) {
        super();
    }

    public async run(): Promise<void> {
        await this.telegramClient.setMyCommands(BOT_COMMANDS);
    }
}
