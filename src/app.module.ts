import {Module} from "@nestjs/common";
import {ConfigModule} from "./config/config.module.js";
import {HealthModule} from "./health/health.module.js";
import {TelegramModule} from "./telegram/telegram.module.js";

@Module({
    imports: [ConfigModule, HealthModule, TelegramModule],
})
export class AppModule {}
