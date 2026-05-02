import {Inject, Injectable, Logger} from "@nestjs/common";
import {ProcessedUpdateService} from "../conversation/processed-update.service";
import {InboundMessage} from "./inbound-message";
import {TELEGRAM_GATEWAY} from "./telegram.types";
import type {TelegramGateway} from "./telegram.types";

@Injectable()
export class DuplicateUpdateHandlerService {
    private readonly logger = new Logger(DuplicateUpdateHandlerService.name);

    public constructor(
        private readonly processedUpdateService: ProcessedUpdateService,
        @Inject(TELEGRAM_GATEWAY) private readonly telegramClient: TelegramGateway
    ) {}

    public async handle(message: InboundMessage): Promise<boolean> {
        const processedUpdate = await this.processedUpdateService.find(message.updateId);
        if (this.processedUpdateService.duplicate(processedUpdate)) {
            this.logger.log(`Ignored duplicate update update_id=${message.updateId}`);
            return true;
        }
        if (this.processedUpdateService.replayable(processedUpdate)) {
            await this.processedUpdateService.resendPendingReply(message, processedUpdate!, this.telegramClient);
            return true;
        }
        return false;
    }
}
