import {Module} from "@nestjs/common";
import {REPLY_CLIENT} from "../config/tokens.js";
import {CodexCliClient} from "../codex/codex-cli.service.js";
import {StorageModule} from "../storage/storage.module.js";
import {ConversationService} from "./conversation.service.js";

@Module({
    imports: [StorageModule],
    providers: [
        CodexCliClient,
        {
            provide: REPLY_CLIENT,
            useExisting: CodexCliClient,
        },
        ConversationService,
    ],
    exports: [ConversationService],
})
export class ConversationModule {}
