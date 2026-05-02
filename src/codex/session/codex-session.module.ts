import {Module} from "@nestjs/common";
import {CodexExecutionModule} from "../execution/codex-execution.module";
import {CodexSessionCompactClientService} from "./codex-session-compact-client.service";

@Module({
    imports: [CodexExecutionModule],
    providers: [CodexSessionCompactClientService],
    exports: [CodexSessionCompactClientService],
})
export class CodexSessionModule {}
