import {Inject, Injectable} from "@nestjs/common";
import {TelegramUpdate} from "./telegram.types";
import {InboundMessage} from "./inbound-message";
import {MESSAGE_CONSTANTS} from "./message-constants";
import {TelegramGateway, TELEGRAM_GATEWAY} from "./telegram.types";

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
