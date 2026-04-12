import {Module} from "@nestjs/common";

import {ConversationModule} from "../conversation/conversation.module.js";
import {ChatRateLimiter} from "../conversation/rate-limiter.service.js";
import {TelegramController} from "./telegram.controller.js";
import {TelegramUpdateParser} from "./telegram-update-parser.service.js";
import {TelegramService} from "./telegram.service.js";
import {TelegramWebhookHandler} from "./telegram-webhook-handler.service.js";

@Module({
    imports: [ConversationModule],
    controllers: [TelegramController],
    providers: [TelegramService, ChatRateLimiter, TelegramUpdateParser, TelegramWebhookHandler],
    exports: [TelegramService, TelegramUpdateParser, TelegramWebhookHandler],
})
export class TelegramModule {}
