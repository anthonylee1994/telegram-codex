import {Module} from "@nestjs/common";
import {CodexModule} from "./codex/codex.module";
import {AppConfigModule} from "./config/config.module";
import {ConversationModule} from "./conversation/conversation.module";
import {DatabaseModule} from "./database/database.module";
import {TelegramModule} from "./telegram/telegram.module";

@Module({
    imports: [AppConfigModule, DatabaseModule, CodexModule, ConversationModule, TelegramModule],
})
export class TaskModule {}
