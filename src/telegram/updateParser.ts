import type {IncomingTelegramMessage} from "../types/conversation.js";

interface TelegramTextMessage {
    message: {
        chat: {
            id: number | string;
        };
        from: {
            id: number | string;
        };
        message_id: number;
        text: string;
    };
    update_id: number;
}

function isTelegramTextMessage(update: unknown): update is TelegramTextMessage {
    if (!update || typeof update !== "object") {
        return false;
    }

    const maybeMessage = update as Partial<TelegramTextMessage>;

    return (
        typeof maybeMessage.update_id === "number" &&
        Boolean(maybeMessage.message) &&
        maybeMessage.message?.from !== undefined &&
        typeof maybeMessage.message?.message_id === "number" &&
        typeof maybeMessage.message?.text === "string" &&
        maybeMessage.message?.chat !== undefined
    );
}

export function parseIncomingTelegramMessage(update: unknown): IncomingTelegramMessage | null {
    if (!isTelegramTextMessage(update)) {
        return null;
    }

    return {
        chatId: String(update.message.chat.id),
        messageId: update.message.message_id,
        text: update.message.text.trim(),
        userId: String(update.message.from.id),
        updateId: update.update_id,
    };
}
