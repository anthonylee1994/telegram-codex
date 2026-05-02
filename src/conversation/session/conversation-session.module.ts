import {Module} from "@nestjs/common";
import {CodexModule} from "../../codex/codex.module";
import {ConversationStorageModule} from "../storage/conversation-storage.module";
import {SessionService} from "./session.service";

@Module({
    imports: [CodexModule, ConversationStorageModule],
    providers: [SessionService],
    exports: [SessionService],
})
export class ConversationSessionModule {}
