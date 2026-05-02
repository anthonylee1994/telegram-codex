import {Inject, Injectable} from "@nestjs/common";
import {TelegramUpdate} from "../shared/telegram.types";
import {InboundMessage} from "../shared/inbound-message";
import {MESSAGE_CONSTANTS} from "../shared/message-constants";
import {TELEGRAM_GATEWAY} from "../shared/telegram.types";
import type {TelegramGateway} from "../shared/telegram.types";

@Injectable()
export class UnsupportedMessageHandlerService {
    public constructor(@Inject(TELEGRAM_GATEWAY) private readonly telegramClient: TelegramGateway) {}

    public async handle(message: InboundMessage | null, update: TelegramUpdate | null | undefined): Promise<boolean> {
        if (message && !message.unsupported()) {
            return false;
        }
        const chatId = message?.chatId ?? update?.message?.chat?.id?.toString();
        if (chatId) {
            await this.telegramClient.sendMessage(chatId, MESSAGE_CONSTANTS.unsupportedMessage, [], false);
        }
        return true;
    }
}
