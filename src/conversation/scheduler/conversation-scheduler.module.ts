import {forwardRef, Module} from "@nestjs/common";
import {TelegramCommandsModule} from "../../telegram/commands/telegram-commands.module";
import {TelegramInboundModule} from "../../telegram/inbound/telegram-inbound.module";
import {ConversationReplyModule} from "../reply/conversation-reply.module";
import {ConversationSessionModule} from "../session/conversation-session.module";
import {ConversationStorageModule} from "../storage/conversation-storage.module";
import {JobSchedulerService} from "./job-scheduler.service";

@Module({
    imports: [ConversationReplyModule, ConversationSessionModule, ConversationStorageModule, forwardRef(() => TelegramCommandsModule), forwardRef(() => TelegramInboundModule)],
    providers: [JobSchedulerService],
    exports: [JobSchedulerService],
})
export class ConversationSchedulerModule {}
