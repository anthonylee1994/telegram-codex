import {Inject, Injectable} from "@nestjs/common";
import {SessionCompactResult} from "../../conversation/session/session.service";
import {TELEGRAM_GATEWAY} from "../shared/telegram.types";
import type {TelegramGateway} from "../shared/telegram.types";

@Injectable()
export class CompactResultSenderService {
    public constructor(@Inject(TELEGRAM_GATEWAY) private readonly telegramClient: TelegramGateway) {}

    public async send(chatId: string, result: SessionCompactResult): Promise<void> {
        const text =
            result.status === "MISSING_SESSION"
                ? "而家冇 active session，冇嘢可以 compact。"
                : result.status === "TOO_SHORT"
                  ? `目前對話得 ${result.messageCount} 段訊息，未去到要壓縮 context。`
                  : ["已經將目前 session compact 成新 context。", `原本訊息：${result.originalMessageCount}`, "", result.compactText].join("\n");
        await this.telegramClient.sendMessage(chatId, text, [], true);
    }
}
