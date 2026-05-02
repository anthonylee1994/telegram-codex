import {Injectable} from "@nestjs/common";
import {AppConfigService} from "../../config/app-config.service";

@Injectable()
export class ChatRateLimiterService {
    private readonly hits = new Map<string, number[]>();

    public constructor(private readonly config: AppConfigService) {}

    public allow(chatId: string): boolean {
        const now = Date.now();
        const chatHits = this.hits.get(chatId) ?? [];
        const retained = chatHits.filter(timestamp => now - timestamp < this.config.rateLimitWindowMs);
        if (retained.length >= this.config.rateLimitMaxMessages) {
            this.hits.set(chatId, retained);
            return false;
        }
        retained.push(now);
        this.hits.set(chatId, retained);
        return true;
    }
}
