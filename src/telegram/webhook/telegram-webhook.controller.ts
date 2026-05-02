import {Body, Controller, Headers, HttpCode, HttpException, HttpStatus, Post} from "@nestjs/common";
import {AppConfigService} from "../../config/app-config.service";
import {ApiStatusResponse} from "../../health/health.controller";
import {TelegramUpdate} from "../shared/telegram.types";
import {TelegramWebhookService} from "./telegram-webhook.service";

@Controller("telegram/webhook")
export class TelegramWebhookController {
    public constructor(
        private readonly config: AppConfigService,
        private readonly webhookHandler: TelegramWebhookService
    ) {}

    @Post()
    @HttpCode(200)
    public async create(@Headers("x-telegram-bot-api-secret-token") secretToken: string | undefined, @Body() payload: TelegramUpdate | null): Promise<ApiStatusResponse> {
        if (this.config.telegramWebhookSecret !== secretToken) {
            throw new HttpException({ok: false}, HttpStatus.UNAUTHORIZED);
        }
        try {
            await this.webhookHandler.handle(payload);
            return {ok: true};
        } catch {
            throw new HttpException({ok: false}, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
