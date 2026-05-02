import {Module} from "@nestjs/common";
import {AppConfigModule} from "../../config/config.module";
import {TelegramInboundModule} from "../inbound/telegram-inbound.module";
import {TelegramSharedModule} from "../shared/telegram-shared.module";
import {TelegramWebhookController} from "./telegram-webhook.controller";
import {TelegramWebhookRouterService, TelegramWebhookService} from "./telegram-webhook.service";

@Module({
    imports: [AppConfigModule, TelegramInboundModule, TelegramSharedModule],
    controllers: [TelegramWebhookController],
    providers: [TelegramWebhookRouterService, TelegramWebhookService],
})
export class TelegramWebhookModule {}
