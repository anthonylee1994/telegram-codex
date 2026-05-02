import {Module} from "@nestjs/common";
import {CodexExecutionModule} from "../execution/codex-execution.module";
import {CodexParsingModule} from "../parsing/codex-parsing.module";
import {CodexMemoryClientService} from "./codex-memory-client.service";

@Module({
    imports: [CodexExecutionModule, CodexParsingModule],
    providers: [CodexMemoryClientService],
    exports: [CodexMemoryClientService],
})
export class CodexMemoryModule {}
