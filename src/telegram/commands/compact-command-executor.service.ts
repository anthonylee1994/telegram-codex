import {Injectable} from "@nestjs/common";
import {CONVERSATION_CONSTANTS} from "../../conversation/conversation.constants";
import {JobSchedulerService} from "../../conversation/scheduler/job-scheduler.service";
import {SessionCompactResult, SessionService, SessionSnapshot} from "../../conversation/session/session.service";
import {InboundMessage} from "../shared/inbound-message";
import {MESSAGE_CONSTANTS} from "../shared/message-constants";
import {TelegramCommandResponderService} from "./telegram-command-responder.service";

@Injectable()
export class CompactCommandExecutorService {
    public constructor(
        private readonly sessionService: SessionService,
        private readonly jobSchedulerService: JobSchedulerService,
        private readonly responder: TelegramCommandResponderService
    ) {}

    public async execute(message: InboundMessage): Promise<void> {
        const snapshot = await this.sessionService.snapshot(message.chatId);
        const immediateResult = this.validate(snapshot);
        if (immediateResult) {
            await this.responder.sendCompactResult(message, immediateResult);
            return;
        }
        this.jobSchedulerService.enqueueSessionCompact(message.chatId);
        await this.responder.reply(message, MESSAGE_CONSTANTS.compactQueuedMessage);
    }

    private validate(snapshot: SessionSnapshot): SessionCompactResult | null {
        if (!snapshot.active) {
            return {status: "MISSING_SESSION", messageCount: null, originalMessageCount: null, compactText: null};
        }
        if (snapshot.messageCount < CONVERSATION_CONSTANTS.minTranscriptSizeForCompact) {
            return {
                status: "TOO_SHORT",
                messageCount: snapshot.messageCount,
                originalMessageCount: null,
                compactText: null,
            };
        }
        return null;
    }
}
