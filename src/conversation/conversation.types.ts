export interface ChatSession {
    chatId: string;
    conversationState: string | null;
    updatedAt: number;
}

export interface IncomingTelegramMessage {
    chatId: string;
    imageFileId: string | null;
    imageFilePath?: string | null;
    messageId: number;
    text: string;
    userId: string;
    updateId: number;
}

export interface GenerateReplyInput {
    chatId: string;
    text: string;
    conversationState: string | null;
    imageFilePath?: string | null;
}

export interface GenerateReplyResult {
    conversationState: string;
    text: string;
}
