import {Global, Module} from "@nestjs/common";

import {loadEnv} from "./env.js";
import {AppLogger} from "./logger.js";
import {APP_ENV, LOGGER} from "./tokens.js";

@Global()
@Module({
    providers: [
        {
            provide: APP_ENV,
            useFactory: loadEnv,
        },
        AppLogger,
        {
            provide: LOGGER,
            useExisting: AppLogger,
        },
    ],
    exports: [APP_ENV, AppLogger, LOGGER],
})
export class ConfigModule {}
