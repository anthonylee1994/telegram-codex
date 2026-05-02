import {forwardRef, Module} from "@nestjs/common";
import {AppConfigModule} from "../../config/config.module";
import {ConversationReplyModule} from "../../conversation/reply/conversation-reply.module";
import {ConversationSchedulerModule} from "../../conversation/scheduler/conversation-scheduler.module";
import {ConversationStorageModule} from "../../conversation/storage/conversation-storage.module";
import {TelegramApiModule} from "../api/telegram-api.module";
import {TelegramCommandsModule} from "../commands/telegram-commands.module";
import {DuplicateUpdateHandlerService} from "./duplicate-update-handler.service";
import {inboundMessageProcessorProvider, InboundMessageProcessorService} from "./inbound-message-processor.service";
import {ReplyRequestGuardService} from "./reply-request-guard.service";
import {UnsupportedMessageHandlerService} from "./unsupported-message-handler.service";

@Module({
    imports: [AppConfigModule, ConversationReplyModule, forwardRef(() => ConversationSchedulerModule), ConversationStorageModule, TelegramApiModule, TelegramCommandsModule],
    providers: [DuplicateUpdateHandlerService, InboundMessageProcessorService, inboundMessageProcessorProvider, ReplyRequestGuardService, UnsupportedMessageHandlerService],
    exports: [inboundMessageProcessorProvider, InboundMessageProcessorService],
})
export class TelegramInboundModule {}
