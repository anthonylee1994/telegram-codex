import {Injectable} from "@nestjs/common";
import {InboundMessage} from "./inbound-message";
import {InboundMessageProcessorService} from "./inbound-message-processor.service";
import {TelegramUpdateParserService} from "./telegram-update-parser.service";
import {TelegramUpdate} from "./telegram.types";

@Injectable()
export class TelegramWebhookRouterService {
    public constructor(private readonly inboundMessageProcessor: InboundMessageProcessorService) {}

    public async route(message: InboundMessage | null, update: TelegramUpdate | null | undefined): Promise<void> {
        if (message?.mediaGroup()) {
            await this.inboundMessageProcessor.deferMediaGroup(message);
            return;
        }
        await this.inboundMessageProcessor.process(message, update);
    }
}

@Injectable()
export class TelegramWebhookService {
    public constructor(
        private readonly telegramUpdateParser: TelegramUpdateParserService,
        private readonly webhookRouter: TelegramWebhookRouterService
    ) {}

    public async handle(update: TelegramUpdate | null | undefined): Promise<void> {
        const message = this.telegramUpdateParser.parseIncomingTelegramMessage(update);
        await this.webhookRouter.route(message, update);
    }
}
