import {Module} from "@nestjs/common";
import {AppConfigModule} from "../../config/config.module";
import {ExecRunnerService} from "./exec-runner.service";

@Module({
    imports: [AppConfigModule],
    providers: [ExecRunnerService],
    exports: [ExecRunnerService],
})
export class CodexExecutionModule {}
