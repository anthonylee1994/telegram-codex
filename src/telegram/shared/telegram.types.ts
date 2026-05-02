export interface TelegramBotCommand {
    command: string;
    description: string;
}

export interface TelegramUpdate {
    update_id?: number;
    message?: TelegramMessage;
}

export interface TelegramMessage {
    message_id?: number;
    from?: TelegramUser;
    chat?: TelegramChat;
    text?: string;
    caption?: string;
    photo?: TelegramPhotoSize[];
    document?: TelegramDocument;
    media_group_id?: string;
    reply_to_message?: TelegramMessage;
}

export interface TelegramUser {
    id?: number | string;
}

export interface TelegramChat {
    id?: number | string;
}

export interface TelegramPhotoSize {
    file_id?: string;
    file_size?: number;
}

export interface TelegramDocument {
    file_id?: string;
    mime_type?: string;
    file_name?: string;
}

export interface TelegramGateway {
    downloadFileToTemp(fileId: string): Promise<string>;
    sendMessage(chatId: string, text: string | null | undefined, suggestedReplies: string[], removeKeyboard: boolean): Promise<void>;
    withTypingStatus<T>(chatId: string, action: () => Promise<T>): Promise<T>;
    setWebhook(url: string, secretToken: string): Promise<void>;
    setMyCommands(commands: TelegramBotCommand[]): Promise<void>;
}

export const TELEGRAM_GATEWAY = Symbol("TELEGRAM_GATEWAY");

export const TELEGRAM_CONSTANTS = {
    maxSuggestedReplies: 3,
    maxSuggestedReplyLength: 40,
    apiBase: "https://api.telegram.org/bot",
    fileApiBase: "https://api.telegram.org/file/bot",
};

export const DOCUMENT_CONSTANTS = {
    imageMimeTypePrefixes: ["image/"],
    imageExtensions: [".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".tiff", ".heic", ".heif"],
};
