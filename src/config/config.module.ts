import {Module} from "@nestjs/common";
import {ConfigModule as NestConfigModule} from "@nestjs/config";
import {appConfig} from "./app-config";
import {AppConfigService} from "./app-config.service";

@Module({
    imports: [
        NestConfigModule.forRoot({
            isGlobal: true,
            load: [appConfig],
        }),
    ],
    providers: [AppConfigService],
    exports: [AppConfigService],
})
export class AppConfigModule {}
