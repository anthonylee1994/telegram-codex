import {Module} from "@nestjs/common";
import {CodexExecutionModule} from "../execution/codex-execution.module";
import {CodexParsingModule} from "../parsing/codex-parsing.module";
import {CodexReplyClientService} from "./codex-reply-client.service";
import {PromptBuilderService} from "./prompt-builder.service";

@Module({
    imports: [CodexExecutionModule, CodexParsingModule],
    providers: [CodexReplyClientService, PromptBuilderService],
    exports: [CodexReplyClientService, PromptBuilderService],
})
export class CodexReplyModule {}
