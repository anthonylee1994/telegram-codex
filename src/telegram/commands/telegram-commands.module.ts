import {forwardRef, Module} from "@nestjs/common";
import {AppConfigModule} from "../../config/config.module";
import {ConversationReplyModule} from "../../conversation/reply/conversation-reply.module";
import {ConversationSchedulerModule} from "../../conversation/scheduler/conversation-scheduler.module";
import {ConversationSessionModule} from "../../conversation/session/conversation-session.module";
import {TelegramApiModule} from "../api/telegram-api.module";
import {CompactCommandExecutorService} from "./compact-command-executor.service";
import {CompactResultSenderService} from "./compact-result-sender.service";
import {TelegramCommandHandlerService} from "./telegram-command-handler.service";
import {TelegramCommandRegistryService} from "./telegram-command-registry.service";
import {TelegramCommandResponderService} from "./telegram-command-responder.service";
import {TelegramSetWebhookCommand, TelegramUpdateCommandsCommand} from "./telegram-task.command";
import {TelegramStatusMessageBuilderService} from "./telegram-status-message-builder.service";

@Module({
    imports: [AppConfigModule, ConversationReplyModule, forwardRef(() => ConversationSchedulerModule), ConversationSessionModule, TelegramApiModule],
    providers: [
        CompactCommandExecutorService,
        CompactResultSenderService,
        TelegramCommandHandlerService,
        TelegramCommandRegistryService,
        TelegramCommandResponderService,
        TelegramSetWebhookCommand,
        TelegramStatusMessageBuilderService,
        TelegramUpdateCommandsCommand,
    ],
    exports: [CompactResultSenderService, TelegramCommandHandlerService],
})
export class TelegramCommandsModule {}
