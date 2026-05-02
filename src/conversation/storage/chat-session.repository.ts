import {Injectable, Logger} from "@nestjs/common";
import {InjectRepository} from "@nestjs/typeorm";
import {Repository} from "typeorm";
import {AppConfigService} from "../../config/app-config.service";
import {ChatSessionEntity} from "../../database/entities";

export interface ChatSessionRecord {
    chatId: string;
    lastResponseId: string | null;
    updatedAt: number;
}

@Injectable()
export class ChatSessionRepository {
    private readonly logger = new Logger(ChatSessionRepository.name);

    public constructor(
        private readonly config: AppConfigService,
        @InjectRepository(ChatSessionEntity)
        private readonly repository: Repository<ChatSessionEntity>
    ) {}

    public async findActive(chatId: string): Promise<ChatSessionRecord | null> {
        const entity = await this.repository.findOneBy({chatId});
        if (!entity) {
            return null;
        }
        if (Date.now() - Number(entity.updatedAt) > this.config.sessionTtlDays * 24 * 60 * 60 * 1000) {
            await this.repository.delete({chatId});
            this.logger.log(`Reset expired session chat_id=${chatId}`);
            return null;
        }
        return {chatId: entity.chatId, lastResponseId: entity.lastResponseId, updatedAt: Number(entity.updatedAt)};
    }

    public async persist(chatId: string, conversationState: string | null | undefined): Promise<void> {
        await this.repository.save({chatId, lastResponseId: conversationState ?? null, updatedAt: Date.now()});
    }

    public async reset(chatId: string): Promise<void> {
        await this.repository.delete({chatId});
        this.logger.log(`Reset chat session chat_id=${chatId}`);
    }
}
