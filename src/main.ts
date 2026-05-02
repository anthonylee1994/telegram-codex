import "reflect-metadata";
import * as fs from "node:fs";
import * as path from "node:path";
import {NestFactory} from "@nestjs/core";
import {AppModule} from "./app.module";
import {AppConfigService} from "./config/app-config.service";

async function bootstrap(): Promise<void> {
    const app = await NestFactory.create(AppModule);
    const config = app.get(AppConfigService);
    fs.mkdirSync(path.dirname(path.resolve(config.sqliteDbPath)), {recursive: true});
    await app.listen(config.port);
}

void bootstrap();
