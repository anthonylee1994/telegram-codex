import {forwardRef, Module} from "@nestjs/common";
import {TypeOrmModule} from "@nestjs/typeorm";
import {CodexModule} from "../codex/codex.module";
import {AppConfigModule} from "../config/config.module";
import {ChatMemoryEntity, ChatSessionEntity, MediaGroupBufferEntity, MediaGroupMessageEntity, ProcessedUpdateEntity} from "../database/entities";
import {TelegramModule} from "../telegram/telegram.module";
import {AttachmentDownloaderService} from "./attachment-downloader.service";
import {ChatMemoryRepository} from "./chat-memory.repository";
import {ChatRateLimiterService} from "./chat-rate-limiter.service";
import {ChatSessionRepository} from "./chat-session.repository";
import {JobSchedulerService} from "./job-scheduler.service";
import {MediaGroupBufferRepository} from "./media-group-buffer.repository";
import {MediaGroupMergerService} from "./media-group-merger.service";
import {ProcessedUpdateRepository} from "./processed-update.repository";
import {ProcessedUpdateService} from "./processed-update.service";
import {ReplyGenerationService} from "./reply-generation.service";
import {SessionService} from "./session.service";

@Module({
    imports: [
        AppConfigModule,
        CodexModule,
        forwardRef(() => TelegramModule),
        TypeOrmModule.forFeature([ChatMemoryEntity, ChatSessionEntity, MediaGroupBufferEntity, MediaGroupMessageEntity, ProcessedUpdateEntity]),
    ],
    providers: [
        AttachmentDownloaderService,
        ChatMemoryRepository,
        ChatRateLimiterService,
        ChatSessionRepository,
        JobSchedulerService,
        MediaGroupBufferRepository,
        MediaGroupMergerService,
        ProcessedUpdateRepository,
        ProcessedUpdateService,
        ReplyGenerationService,
        SessionService,
    ],
    exports: [ChatRateLimiterService, JobSchedulerService, MediaGroupBufferRepository, ProcessedUpdateService, SessionService],
})
export class ConversationModule {}
