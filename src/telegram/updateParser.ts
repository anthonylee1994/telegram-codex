import type {IncomingTelegramMessage} from "../types/conversation.js";

interface TelegramPhotoSize {
    file_id: string;
    file_size?: number;
}

interface TelegramSupportedMessage {
    message: {
        caption?: string;
        chat: {
            id: number | string;
        };
        from: {
            id: number | string;
        };
        message_id: number;
        photo?: TelegramPhotoSize[];
        text?: string;
    };
    update_id: number;
}

function isTelegramSupportedMessage(update: unknown): update is TelegramSupportedMessage {
    if (!update || typeof update !== "object") {
        return false;
    }

    const maybeMessage = update as Partial<TelegramSupportedMessage>;
    const hasText = typeof maybeMessage.message?.text === "string";
    const hasPhoto = Array.isArray(maybeMessage.message?.photo) && maybeMessage.message.photo.length > 0;

    return (
        typeof maybeMessage.update_id === "number" &&
        Boolean(maybeMessage.message) &&
        maybeMessage.message?.from !== undefined &&
        typeof maybeMessage.message?.message_id === "number" &&
        (hasText || hasPhoto) &&
        maybeMessage.message?.chat !== undefined
    );
}

export function parseIncomingTelegramMessage(update: unknown): IncomingTelegramMessage | null {
    if (!isTelegramSupportedMessage(update)) {
        return null;
    }

    const largestPhoto = update.message.photo?.reduce(function selectLargestPhoto(current: TelegramPhotoSize | undefined, candidate: TelegramPhotoSize): TelegramPhotoSize {
        if (!current) {
            return candidate;
        }

        return (candidate.file_size ?? 0) >= (current.file_size ?? 0) ? candidate : current;
    }, undefined);

    return {
        chatId: String(update.message.chat.id),
        imageFileId: largestPhoto?.file_id ?? null,
        messageId: update.message.message_id,
        text: (update.message.text ?? update.message.caption ?? "").trim(),
        userId: String(update.message.from.id),
        updateId: update.update_id,
    };
}
