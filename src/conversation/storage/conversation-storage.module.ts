import {Module} from "@nestjs/common";
import {TypeOrmModule} from "@nestjs/typeorm";
import {AppConfigModule} from "../../config/config.module";
import {ChatMemoryEntity, ChatSessionEntity, MediaGroupBufferEntity, MediaGroupMessageEntity, ProcessedUpdateEntity} from "../../database/entities";
import {ChatMemoryRepository} from "./chat-memory.repository";
import {ChatSessionRepository} from "./chat-session.repository";
import {MediaGroupBufferRepository} from "./media-group-buffer.repository";
import {MediaGroupMergerService} from "./media-group-merger.service";
import {ProcessedUpdateRepository} from "./processed-update.repository";

@Module({
    imports: [AppConfigModule, TypeOrmModule.forFeature([ChatMemoryEntity, ChatSessionEntity, MediaGroupBufferEntity, MediaGroupMessageEntity, ProcessedUpdateEntity])],
    providers: [ChatMemoryRepository, ChatSessionRepository, MediaGroupBufferRepository, MediaGroupMergerService, ProcessedUpdateRepository],
    exports: [ChatMemoryRepository, ChatSessionRepository, MediaGroupBufferRepository, ProcessedUpdateRepository],
})
export class ConversationStorageModule {}
