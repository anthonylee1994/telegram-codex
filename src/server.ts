import "reflect-metadata";
import {NestFactory} from "@nestjs/core";
import {AppModule} from "./app.module.js";
import {AppLogger, createScopedLogger} from "./config/logger.js";
import {APP_ENV, LOGGER} from "./config/tokens.js";
import type {AppEnv} from "./config/env.js";
import type {Logger} from "./config/service.types.js";

export async function bootstrap(): Promise<void> {
    const app = await NestFactory.create(AppModule, {
        bufferLogs: true,
        logger: false,
    });
    app.useLogger(app.get(AppLogger));

    const env = app.get<AppEnv>(APP_ENV);
    const logger = createScopedLogger(app.get<Logger>(LOGGER), "Bootstrap");

    await app.listen(env.PORT);

    logger.info("Server started", {
        port: env.PORT,
    });
}

void bootstrap();
