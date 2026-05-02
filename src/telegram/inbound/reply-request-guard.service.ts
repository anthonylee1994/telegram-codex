import {Inject, Injectable, Logger} from "@nestjs/common";
import {AppConfigService} from "../../config/app-config.service";
import {ChatRateLimiterService} from "../../conversation/reply/chat-rate-limiter.service";
import {ProcessedUpdateService} from "../../conversation/reply/processed-update.service";
import {InboundMessage} from "../shared/inbound-message";
import {MESSAGE_CONSTANTS} from "../shared/message-constants";
import {TELEGRAM_GATEWAY} from "../shared/telegram.types";
import type {TelegramGateway} from "../shared/telegram.types";

@Injectable()
export class ReplyRequestGuardService {
    private readonly logger = new Logger(ReplyRequestGuardService.name);

    public constructor(
        private readonly config: AppConfigService,
        private readonly rateLimiter: ChatRateLimiterService,
        private readonly processedUpdateService: ProcessedUpdateService,
        @Inject(TELEGRAM_GATEWAY) private readonly telegramClient: TelegramGateway
    ) {}

    public async allow(message: InboundMessage): Promise<boolean> {
        return (await this.allowAuthorizedUser(message)) && (await this.allowSupportedMediaGroupSize(message)) && (await this.allowChatRate(message)) && (await this.beginProcessing(message));
    }

    private async sendAndMarkProcessed(message: InboundMessage, text: string): Promise<void> {
        await this.telegramClient.sendMessage(message.chatId, text, [], false);
        await this.processedUpdateService.markProcessed(message);
    }

    private async allowAuthorizedUser(message: InboundMessage): Promise<boolean> {
        if (this.config.allowedTelegramUserIds.length === 0 || this.config.allowedTelegramUserIds.includes(message.userId)) {
            return true;
        }
        this.logger.warn(`Rejected unauthorized Telegram user chat_id=${message.chatId} user_id=${message.userId}`);
        await this.sendAndMarkProcessed(message, MESSAGE_CONSTANTS.unauthorizedMessage);
        return false;
    }

    private async allowSupportedMediaGroupSize(message: InboundMessage): Promise<boolean> {
        if (!message.mediaGroup() || message.imageCount() <= this.config.maxMediaGroupImages) {
            return true;
        }
        await this.sendAndMarkProcessed(message, MESSAGE_CONSTANTS.tooManyImagesMessage);
        return false;
    }

    private async allowChatRate(message: InboundMessage): Promise<boolean> {
        if (this.rateLimiter.allow(message.chatId)) {
            return true;
        }
        await this.sendAndMarkProcessed(message, MESSAGE_CONSTANTS.rateLimitMessage);
        return false;
    }

    private async beginProcessing(message: InboundMessage): Promise<boolean> {
        if (await this.processedUpdateService.beginProcessing(message)) {
            return true;
        }
        this.logger.log(`Ignored duplicate update update_id=${message.updateId}`);
        return false;
    }
}
