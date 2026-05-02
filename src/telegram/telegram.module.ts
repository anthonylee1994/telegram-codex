import {forwardRef, Module} from "@nestjs/common";
import {AppConfigModule} from "../config/config.module";
import {ConversationModule} from "../conversation/conversation.module";
import {CodexModule} from "../codex/codex.module";
import {CompactCommandExecutorService} from "./compact-command-executor.service";
import {CompactResultSenderService} from "./compact-result-sender.service";
import {DuplicateUpdateHandlerService} from "./duplicate-update-handler.service";
import {inboundMessageProcessorProvider, InboundMessageProcessorService} from "./inbound-message-processor.service";
import {ReplyRequestGuardService} from "./reply-request-guard.service";
import {TELEGRAM_GATEWAY} from "./telegram.types";
import {TelegramApiService} from "./telegram-api.service";
import {TelegramCommandHandlerService} from "./telegram-command-handler.service";
import {TelegramCommandRegistryService} from "./telegram-command-registry.service";
import {TelegramCommandResponderService} from "./telegram-command-responder.service";
import {TelegramMessageFormatterService} from "./telegram-message-formatter.service";
import {TelegramSetWebhookCommand, TelegramUpdateCommandsCommand} from "./telegram-task.command";
import {TelegramStatusMessageBuilderService} from "./telegram-status-message-builder.service";
import {TelegramUpdateParserService} from "./telegram-update-parser.service";
import {TelegramWebhookController} from "./telegram-webhook.controller";
import {TelegramWebhookRouterService, TelegramWebhookService} from "./telegram-webhook.service";
import {TypingStatusService} from "./typing-status.service";
import {UnsupportedMessageHandlerService} from "./unsupported-message-handler.service";

@Module({
    imports: [AppConfigModule, CodexModule, forwardRef(() => ConversationModule)],
    controllers: [TelegramWebhookController],
    providers: [
        CompactCommandExecutorService,
        CompactResultSenderService,
        DuplicateUpdateHandlerService,
        InboundMessageProcessorService,
        inboundMessageProcessorProvider,
        ReplyRequestGuardService,
        TelegramApiService,
        {provide: TELEGRAM_GATEWAY, useExisting: TelegramApiService},
        TelegramCommandHandlerService,
        TelegramCommandRegistryService,
        TelegramCommandResponderService,
        TelegramMessageFormatterService,
        TelegramSetWebhookCommand,
        TelegramStatusMessageBuilderService,
        TelegramUpdateCommandsCommand,
        TelegramUpdateParserService,
        TelegramWebhookRouterService,
        TelegramWebhookService,
        TypingStatusService,
        UnsupportedMessageHandlerService,
    ],
  exports: [
    CompactResultSenderService,
    inboundMessageProcessorProvider,
    TELEGRAM_GATEWAY,
    TelegramMessageFormatterService,
  ],
})
export class TelegramModule {}
