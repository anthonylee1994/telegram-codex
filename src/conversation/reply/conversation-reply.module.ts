import {forwardRef, Module} from "@nestjs/common";
import {CodexModule} from "../../codex/codex.module";
import {AppConfigModule} from "../../config/config.module";
import {TelegramApiModule} from "../../telegram/api/telegram-api.module";
import {ConversationSessionModule} from "../session/conversation-session.module";
import {ConversationStorageModule} from "../storage/conversation-storage.module";
import {AttachmentDownloaderService} from "./attachment-downloader.service";
import {ChatRateLimiterService} from "./chat-rate-limiter.service";
import {ProcessedUpdateService} from "./processed-update.service";
import {ReplyGenerationService} from "./reply-generation.service";

@Module({
    imports: [AppConfigModule, CodexModule, ConversationSessionModule, ConversationStorageModule, forwardRef(() => TelegramApiModule)],
    providers: [AttachmentDownloaderService, ChatRateLimiterService, ProcessedUpdateService, ReplyGenerationService],
    exports: [ChatRateLimiterService, ProcessedUpdateService, ReplyGenerationService],
})
export class ConversationReplyModule {}
