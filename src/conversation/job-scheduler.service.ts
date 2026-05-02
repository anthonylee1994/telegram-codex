import {forwardRef, Inject, Injectable, Logger, OnModuleDestroy} from "@nestjs/common";
import {InboundMessage} from "../telegram/inbound-message";
import {CompactResultSenderService} from "../telegram/compact-result-sender.service";
import {MediaGroupBufferRepository} from "./media-group-buffer.repository";
import {ReplyGenerationService} from "./reply-generation.service";
import {SessionService} from "./session.service";

export interface InboundMessageProcessorPort {
    process(message: InboundMessage | null, update?: unknown): Promise<void>;
}

export const INBOUND_MESSAGE_PROCESSOR = Symbol("INBOUND_MESSAGE_PROCESSOR");

@Injectable()
export class JobSchedulerService implements OnModuleDestroy {
    private readonly logger = new Logger(JobSchedulerService.name);
    private readonly timers = new Set<NodeJS.Timeout>();

    public constructor(
        private readonly mediaGroupStore: MediaGroupBufferRepository,
        private readonly replyGenerationService: ReplyGenerationService,
        private readonly sessionService: SessionService,
        @Inject(forwardRef(() => CompactResultSenderService))
        private readonly compactResultSender: CompactResultSenderService,
        @Inject(INBOUND_MESSAGE_PROCESSOR)
        private readonly inboundMessageProcessor: InboundMessageProcessorPort
    ) {}

    public enqueueReplyGeneration(message: InboundMessage): void {
        void this.replyGenerationService.handle(message).catch((error: unknown) => {
            this.logger.error(`Reply generation failed update_id=${message.updateId}`, error as Error);
        });
    }

    public scheduleMediaGroupFlush(key: string, expectedDeadlineAt: number, waitDurationMs: number): void {
        const timer = setTimeout(() => {
            this.timers.delete(timer);
            void this.flushMediaGroup(key, expectedDeadlineAt);
        }, waitDurationMs);
        this.timers.add(timer);
    }

    public enqueueSessionCompact(chatId: string): void {
        void this.sessionService
            .compact(chatId)
            .then(result => this.compactResultSender.send(chatId, result))
            .catch((error: unknown) => {
                this.logger.error(`Session compact failed chat_id=${chatId}`, error as Error);
            });
    }

    public onModuleDestroy(): void {
        for (const timer of this.timers) {
            clearTimeout(timer);
        }
        this.timers.clear();
    }

    private async flushMediaGroup(key: string, expectedDeadlineAt: number): Promise<void> {
        const result = await this.mediaGroupStore.flush(key, expectedDeadlineAt);
        if (result.kind === "ready") {
            await this.inboundMessageProcessor.process(result.message);
        }
        if (result.kind === "pending") {
            this.scheduleMediaGroupFlush(key, expectedDeadlineAt, Math.round(result.waitDurationSeconds * 1000));
        }
    }
}
