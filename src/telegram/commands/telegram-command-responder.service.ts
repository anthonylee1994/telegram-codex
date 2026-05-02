import {Inject, Injectable} from "@nestjs/common";
import {ProcessedUpdateService} from "../../conversation/reply/processed-update.service";
import {SessionCompactResult} from "../../conversation/session/session.service";
import {InboundMessage} from "../shared/inbound-message";
import {CompactResultSenderService} from "./compact-result-sender.service";
import {TELEGRAM_GATEWAY} from "../shared/telegram.types";
import type {TelegramGateway} from "../shared/telegram.types";

@Injectable()
export class TelegramCommandResponderService {
    public constructor(
        private readonly processedUpdateService: ProcessedUpdateService,
        @Inject(TELEGRAM_GATEWAY) private readonly telegramClient: TelegramGateway,
        private readonly compactResultSender: CompactResultSenderService
    ) {}

    public async reply(message: InboundMessage, text: string): Promise<void> {
        await this.telegramClient.sendMessage(message.chatId, text, [], true);
        await this.processedUpdateService.markProcessed(message);
    }

    public async sendCompactResult(message: InboundMessage, result: SessionCompactResult): Promise<void> {
        await this.compactResultSender.send(message.chatId, result);
        await this.processedUpdateService.markProcessed(message);
    }
}
