import {Module} from "@nestjs/common";
import {CodexExecutionModule} from "./execution/codex-execution.module";
import {CodexMemoryModule} from "./memory/codex-memory.module";
import {CodexParsingModule} from "./parsing/codex-parsing.module";
import {CodexReplyModule} from "./reply/codex-reply.module";
import {CodexSessionModule} from "./session/codex-session.module";

@Module({
    imports: [CodexExecutionModule, CodexMemoryModule, CodexParsingModule, CodexReplyModule, CodexSessionModule],
    exports: [CodexExecutionModule, CodexMemoryModule, CodexParsingModule, CodexReplyModule, CodexSessionModule],
})
export class CodexModule {}
