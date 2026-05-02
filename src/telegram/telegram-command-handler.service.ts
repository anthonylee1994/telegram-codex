import {Injectable} from "@nestjs/common";
import {SessionService} from "../conversation/session.service";
import {InboundMessage} from "./inbound-message";
import {MESSAGE_CONSTANTS} from "./message-constants";
import {CompactCommandExecutorService} from "./compact-command-executor.service";
import {TelegramCommandRegistryService} from "./telegram-command-registry.service";
import {TelegramCommandResponderService} from "./telegram-command-responder.service";
import {TelegramStatusMessageBuilderService} from "./telegram-status-message-builder.service";

@Injectable()
export class TelegramCommandHandlerService {
    public constructor(
        private readonly commandRegistry: TelegramCommandRegistryService,
        private readonly sessionService: SessionService,
        private readonly messageBuilder: TelegramStatusMessageBuilderService,
        private readonly responder: TelegramCommandResponderService,
        private readonly compactCommandExecutor: CompactCommandExecutorService
    ) {}

    public async handle(message: InboundMessage): Promise<boolean> {
        const command = this.commandRegistry.resolve(message);
        if (!command) {
            return false;
        }
        if (command === "START") {
            await this.sessionService.reset(message.chatId);
            await this.responder.reply(message, MESSAGE_CONSTANTS.startMessage);
        } else if (command === "NEW_SESSION") {
            await this.sessionService.reset(message.chatId);
            await this.responder.reply(message, MESSAGE_CONSTANTS.newSessionMessage);
        } else if (command === "HELP") {
            await this.responder.reply(message, MESSAGE_CONSTANTS.helpMessage);
        } else if (command === "STATUS") {
            await this.responder.reply(message, await this.messageBuilder.buildStatusMessage(message.chatId));
        } else if (command === "SESSION") {
            await this.responder.reply(message, await this.messageBuilder.buildSessionMessage(message.chatId));
        } else if (command === "MEMORY") {
            await this.responder.reply(message, await this.messageBuilder.buildMemoryMessage(message.chatId));
        } else if (command === "FORGET") {
            await this.sessionService.resetMemory(message.chatId);
            await this.responder.reply(message, MESSAGE_CONSTANTS.resetMemoryMessage);
        } else if (command === "COMPACT") {
            await this.compactCommandExecutor.execute(message);
        }
        return true;
    }
}
