import * as fs from "node:fs";
import * as path from "node:path";
import {Module} from "@nestjs/common";
import {TypeOrmModule} from "@nestjs/typeorm";
import {AppConfigModule} from "../config/config.module";
import {AppConfigService} from "../config/app-config.service";
import {databaseEntities} from "./entities";
import {CreateAppTables1700000000000} from "./migrations/1700000000000-create-app-tables";

@Module({
    imports: [
        TypeOrmModule.forRootAsync({
            imports: [AppConfigModule],
            inject: [AppConfigService],
            useFactory: (config: AppConfigService) => {
                fs.mkdirSync(path.dirname(path.resolve(config.sqliteDbPath)), {recursive: true});
                return {
                    type: "sqlite",
                    database: config.sqliteDbPath,
                    entities: databaseEntities,
                    migrations: [CreateAppTables1700000000000],
                    migrationsRun: true,
                    synchronize: false,
                };
            },
        }),
    ],
})
export class DatabaseModule {}
