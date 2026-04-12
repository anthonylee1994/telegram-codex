import {Module} from "@nestjs/common";

import {PROCESSED_UPDATE_REPOSITORY, SESSION_REPOSITORY} from "../config/tokens.js";
import {SqliteStorage} from "./sqlite.service.js";

@Module({
    providers: [
        SqliteStorage,
        {
            provide: SESSION_REPOSITORY,
            useExisting: SqliteStorage,
        },
        {
            provide: PROCESSED_UPDATE_REPOSITORY,
            useExisting: SqliteStorage,
        },
    ],
    exports: [SqliteStorage, SESSION_REPOSITORY, PROCESSED_UPDATE_REPOSITORY],
})
export class StorageModule {}
