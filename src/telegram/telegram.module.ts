import {Module} from "@nestjs/common";
import {TelegramApiModule} from "./api/telegram-api.module";
import {TelegramCommandsModule} from "./commands/telegram-commands.module";
import {TelegramInboundModule} from "./inbound/telegram-inbound.module";
import {TelegramSharedModule} from "./shared/telegram-shared.module";
import {TelegramWebhookModule} from "./webhook/telegram-webhook.module";

@Module({
    imports: [TelegramApiModule, TelegramCommandsModule, TelegramInboundModule, TelegramSharedModule, TelegramWebhookModule],
    exports: [TelegramApiModule, TelegramCommandsModule, TelegramInboundModule, TelegramSharedModule],
})
export class TelegramModule {}
