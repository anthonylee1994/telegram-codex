import {Module} from "@nestjs/common";
import {CodexModule} from "../../codex/codex.module";
import {TelegramMessageFormatterService} from "./telegram-message-formatter.service";
import {TelegramUpdateParserService} from "./telegram-update-parser.service";

@Module({
    imports: [CodexModule],
    providers: [TelegramMessageFormatterService, TelegramUpdateParserService],
    exports: [TelegramMessageFormatterService, TelegramUpdateParserService],
})
export class TelegramSharedModule {}
