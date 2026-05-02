import {Column, Entity, Index, PrimaryColumn} from "typeorm";

@Entity("chat_sessions")
export class ChatSessionEntity {
    @PrimaryColumn({name: "chat_id", type: "text"})
    public chatId!: string;

    @Column({name: "last_response_id", type: "text", nullable: true})
    public lastResponseId!: string | null;

    @Column({name: "updated_at", type: "bigint"})
    public updatedAt!: number;
}

@Entity("chat_memories")
export class ChatMemoryEntity {
    @PrimaryColumn({name: "chat_id", type: "text"})
    public chatId!: string;

    @Column({name: "memory_text", type: "text", nullable: true})
    public memoryText!: string | null;

    @Column({name: "updated_at", type: "bigint"})
    public updatedAt!: number;
}

@Entity("processed_updates")
@Index("idx_processed_updates_chat_id", ["chatId"])
export class ProcessedUpdateEntity {
    @PrimaryColumn({name: "update_id", type: "bigint"})
    public updateId!: number;

    @Column({name: "chat_id", type: "text"})
    public chatId!: string;

    @Column({name: "message_id", type: "bigint"})
    public messageId!: number;

    @Column({name: "processed_at", type: "bigint"})
    public processedAt!: number;

    @Column({name: "reply_text", type: "text", nullable: true})
    public replyText!: string | null;

    @Column({name: "conversation_state", type: "text", nullable: true})
    public conversationState!: string | null;

    @Column({name: "suggested_replies", type: "text", nullable: true})
    public suggestedReplies!: string | null;

    @Column({name: "sent_at", type: "bigint", nullable: true})
    public sentAt!: number | null;
}

@Entity("media_group_buffers")
export class MediaGroupBufferEntity {
    @PrimaryColumn({name: "key", type: "text"})
    public key!: string;

    @Column({name: "deadline_at", type: "bigint"})
    public deadlineAt!: number;
}

@Entity("media_group_messages")
@Index("idx_media_group_messages_media_group_key", ["mediaGroupKey"])
export class MediaGroupMessageEntity {
    @PrimaryColumn({name: "update_id", type: "bigint"})
    public updateId!: number;

    @Column({name: "media_group_key", type: "text"})
    public mediaGroupKey!: string;

    @Column({name: "message_id", type: "bigint"})
    public messageId!: number;

    @Column({name: "payload", type: "text"})
    public payload!: string;
}

export const databaseEntities = [ChatSessionEntity, ChatMemoryEntity, ProcessedUpdateEntity, MediaGroupBufferEntity, MediaGroupMessageEntity];
