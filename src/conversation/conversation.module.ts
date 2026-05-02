import {Module} from "@nestjs/common";
import {ConversationReplyModule} from "./reply/conversation-reply.module";
import {ConversationSchedulerModule} from "./scheduler/conversation-scheduler.module";
import {ConversationSessionModule} from "./session/conversation-session.module";
import {ConversationStorageModule} from "./storage/conversation-storage.module";

@Module({
    imports: [ConversationReplyModule, ConversationSchedulerModule, ConversationSessionModule, ConversationStorageModule],
    exports: [ConversationReplyModule, ConversationSchedulerModule, ConversationSessionModule, ConversationStorageModule],
})
export class ConversationModule {}
