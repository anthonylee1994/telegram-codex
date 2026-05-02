import {Injectable} from "@nestjs/common";
import {InboundMessage} from "../shared/inbound-message";
import {InboundMessageProcessorService} from "../inbound/inbound-message-processor.service";
import {TelegramUpdateParserService} from "../shared/telegram-update-parser.service";
import {TelegramUpdate} from "../shared/telegram.types";

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
