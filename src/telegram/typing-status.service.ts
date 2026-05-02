import {Injectable, Logger} from "@nestjs/common";

@Injectable()
export class TypingStatusService {
    private readonly logger = new Logger(TypingStatusService.name);

    public async withTypingStatus<T>(chatId: string, sendTypingAction: (chatId: string) => Promise<void>, action: () => Promise<T>): Promise<T> {
        try {
            await sendTypingAction(chatId);
        } catch (error) {
            this.logger.debug(`Failed to send initial typing status for chat_id=${chatId}`, error as Error);
        }
        const timer = setInterval(() => {
            void sendTypingAction(chatId).catch((error: unknown) => {
                this.logger.debug(`Failed to send periodic typing status for chat_id=${chatId}`, error as Error);
            });
        }, 4000);
        try {
            return await action();
        } finally {
            clearInterval(timer);
        }
    }
}
