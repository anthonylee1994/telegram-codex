import {Injectable, Logger} from "@nestjs/common";
import {InjectRepository} from "@nestjs/typeorm";
import {Repository} from "typeorm";
import {ChatMemoryEntity} from "../database/entities";

export interface ChatMemoryRecord {
    chatId: string;
    memoryText: string | null;
    updatedAt: number;
}

@Injectable()
export class ChatMemoryRepository {
    private readonly logger = new Logger(ChatMemoryRepository.name);

    public constructor(
        @InjectRepository(ChatMemoryEntity)
        private readonly repository: Repository<ChatMemoryEntity>
    ) {}

    public async find(chatId: string): Promise<ChatMemoryRecord | null> {
        const entity = await this.repository.findOneBy({chatId});
        return entity ? {chatId: entity.chatId, memoryText: entity.memoryText, updatedAt: Number(entity.updatedAt)} : null;
    }

    public async persist(chatId: string, memoryText: string | null | undefined): Promise<void> {
        const normalized = (memoryText ?? "").trim();
        if (!normalized) {
            await this.reset(chatId);
            return;
        }
        await this.repository.save({chatId, memoryText: normalized, updatedAt: Date.now()});
    }

    public async reset(chatId: string): Promise<void> {
        await this.repository.delete({chatId});
        this.logger.log(`Reset chat memory chat_id=${chatId}`);
    }
}
