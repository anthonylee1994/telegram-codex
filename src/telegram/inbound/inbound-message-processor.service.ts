import {Injectable} from "@nestjs/common";
import {AppConfigService} from "../../config/app-config.service";
import {INBOUND_MESSAGE_PROCESSOR, JobSchedulerService} from "../../conversation/scheduler/job-scheduler.service";
import {MediaGroupBufferRepository} from "../../conversation/storage/media-group-buffer.repository";
import {InboundMessage} from "../shared/inbound-message";
import {DuplicateUpdateHandlerService} from "./duplicate-update-handler.service";
import {ReplyRequestGuardService} from "./reply-request-guard.service";
import {TelegramCommandHandlerService} from "../commands/telegram-command-handler.service";
import {TelegramUpdate} from "../shared/telegram.types";
import {UnsupportedMessageHandlerService} from "./unsupported-message-handler.service";

@Injectable()
export class InboundMessageProcessorService {
    public constructor(
        private readonly unsupportedMessageHandler: UnsupportedMessageHandlerService,
        private readonly duplicateUpdateHandler: DuplicateUpdateHandlerService,
        private readonly telegramCommandHandler: TelegramCommandHandlerService,
        private readonly replyRequestGuard: ReplyRequestGuardService,
        private readonly mediaGroupStore: MediaGroupBufferRepository,
        private readonly jobSchedulerService: JobSchedulerService,
        private readonly config: AppConfigService
    ) {}

    public async process(message: InboundMessage | null, update: TelegramUpdate | null = null): Promise<void> {
        if (!message) {
            await this.unsupportedMessageHandler.handle(null, update);
            return;
        }
        if (await this.unsupportedMessageHandler.handle(message, update)) {
            return;
        }
        if (await this.duplicateUpdateHandler.handle(message)) {
            return;
        }
        if (await this.telegramCommandHandler.handle(message)) {
            return;
        }
        if (!(await this.replyRequestGuard.allow(message))) {
            return;
        }
        this.jobSchedulerService.enqueueReplyGeneration(message);
    }

    public async deferMediaGroup(message: InboundMessage): Promise<void> {
        const result = await this.mediaGroupStore.enqueue(message, this.config.mediaGroupWaitMs / 1000);
        this.jobSchedulerService.scheduleMediaGroupFlush(result.key, result.deadlineAt, this.config.mediaGroupWaitMs);
    }
}

export const inboundMessageProcessorProvider = {
    provide: INBOUND_MESSAGE_PROCESSOR,
    useExisting: InboundMessageProcessorService,
};
