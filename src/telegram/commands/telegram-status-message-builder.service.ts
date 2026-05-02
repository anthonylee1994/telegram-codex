import {Injectable} from "@nestjs/common";
import {SessionService, SessionSnapshot} from "../../conversation/session/session.service";

@Injectable()
export class TelegramStatusMessageBuilderService {
    public constructor(private readonly sessionService: SessionService) {}

    public async buildStatusMessage(chatId: string): Promise<string> {
        const snapshot = await this.sessionService.snapshot(chatId);
        return ["Bot 狀態：OK 🤖", `Session 狀態：${this.renderSessionStatus(snapshot)}`, "只支持：文字、圖片"].join("\n");
    }

    public async buildSessionMessage(chatId: string): Promise<string> {
        const snapshot = await this.sessionService.snapshot(chatId);
        if (!snapshot.active) {
            return "目前未有已生效 session。你可以直接 send 訊息開始，或者之後打 /compact 壓縮長對話。";
        }
        return ["目前 session：已生效", `訊息數：${snapshot.messageCount}`, `大概輪數：${snapshot.turnCount}`, `最後更新：${snapshot.lastUpdatedAt}`, "想壓縮 context 可以打 /compact。"].join("\n");
    }

    public async buildMemoryMessage(chatId: string): Promise<string> {
        const snapshot = await this.sessionService.memorySnapshot(chatId);
        if (!snapshot.active) {
            return "目前未有長期記憶。你可以直接叫我記住、改寫或者刪除長期記憶；我之後亦會自動記低穩定偏好同持續背景。想清除可以打 /forget。";
        }
        return ["長期記憶：已生效", `最後更新：${snapshot.lastUpdatedAt}`, "", snapshot.memoryText, "", "你可以直接叫我改寫或者刪除長期記憶。", "想清除可以打 /forget。"].join("\n");
    }

    private renderSessionStatus(snapshot: SessionSnapshot): string {
        return snapshot.active ? "已生效" : "未生效";
    }
}
