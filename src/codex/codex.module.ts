import {Module} from "@nestjs/common";
import {AppConfigModule} from "../config/config.module";
import {CodexMemoryClientService} from "./codex-memory-client.service";
import {CodexReplyClientService} from "./codex-reply-client.service";
import {CodexSessionCompactClientService} from "./codex-session-compact-client.service";
import {ExecRunnerService} from "./exec-runner.service";
import {JsonPayloadParserService} from "./json-payload-parser.service";
import {PromptBuilderService} from "./prompt-builder.service";
import {ReplyParserService} from "./reply-parser.service";

@Module({
    imports: [AppConfigModule],
    providers: [CodexMemoryClientService, CodexReplyClientService, CodexSessionCompactClientService, ExecRunnerService, JsonPayloadParserService, PromptBuilderService, ReplyParserService],
    exports: [CodexMemoryClientService, CodexReplyClientService, CodexSessionCompactClientService, ExecRunnerService, JsonPayloadParserService, PromptBuilderService, ReplyParserService],
})
export class CodexModule {}
