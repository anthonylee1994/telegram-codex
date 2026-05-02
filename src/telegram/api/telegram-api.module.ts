import {Module} from "@nestjs/common";
import {AppConfigModule} from "../../config/config.module";
import {TelegramSharedModule} from "../shared/telegram-shared.module";
import {TELEGRAM_GATEWAY} from "../shared/telegram.types";
import {TelegramApiService} from "./telegram-api.service";
import {TypingStatusService} from "./typing-status.service";

@Module({
    imports: [AppConfigModule, TelegramSharedModule],
    providers: [TelegramApiService, TypingStatusService, {provide: TELEGRAM_GATEWAY, useExisting: TelegramApiService}],
    exports: [TELEGRAM_GATEWAY, TelegramApiService],
})
export class TelegramApiModule {}
