export class ChatRateLimiter {
    private readonly hits = new Map<string, number[]>();

    public constructor(
        private readonly windowMs: number,
        private readonly maxMessages: number
    ) {}

    public allow(chatId: string, now: number = Date.now()): boolean {
        const timestamps = this.hits.get(chatId) ?? [];
        const freshTimestamps = timestamps.filter(timestamp => now - timestamp < this.windowMs);

        if (freshTimestamps.length >= this.maxMessages) {
            this.hits.set(chatId, freshTimestamps);
            return false;
        }

        freshTimestamps.push(now);
        this.hits.set(chatId, freshTimestamps);

        return true;
    }
}
