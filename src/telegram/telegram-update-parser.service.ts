import {Injectable} from "@nestjs/common";
import {InboundMessage} from "./inbound-message";
import {DOCUMENT_CONSTANTS, TelegramDocument, TelegramMessage, TelegramUpdate} from "./telegram.types";
import {MESSAGE_CONSTANTS} from "./message-constants";

function stringValue(value: unknown): string {
    return value === null || value === undefined ? "" : String(value);
}

function blankToNull(value: string): string | null {
    const normalized = value.trim();
    return normalized.length === 0 ? null : normalized;
}

class MessageExtractor {
    public constructor(private readonly message: TelegramMessage) {}

    public getChatId(): string {
        return stringValue(this.message.chat?.id);
    }

    public getMessageId(): number {
        return this.message.message_id ?? 0;
    }

    public getUserId(): string {
        return stringValue(this.message.from?.id);
    }

    public getMediaGroupId(): string | null {
        return blankToNull(stringValue(this.message.media_group_id));
    }

    public getText(): string | null {
        const text = stringValue(this.message.text);
        if (text.trim()) {
            return blankToNull(text);
        }
        return blankToNull(stringValue(this.message.caption));
    }

    public getImageFileIds(): string[] {
        const imageDocumentFileId = this.imageDocumentFileId();
        if (imageDocumentFileId) {
            return [imageDocumentFileId];
        }
        const photoFileId = this.photoFileId();
        return photoFileId ? [photoFileId] : [];
    }

    public getReplyToMessage(): MessageExtractor | null {
        return this.message.reply_to_message ? new MessageExtractor(this.message.reply_to_message) : null;
    }

    public getReplyToText(): string | null {
        const reply = this.getReplyToMessage();
        if (!reply) {
            return null;
        }
        const text = reply.getText();
        if (text) {
            return text;
        }
        if (reply.hasPhoto()) {
            return MESSAGE_CONSTANTS.replyToImage;
        }
        if (reply.imageDocumentFileId()) {
            return MESSAGE_CONSTANTS.replyToImageDocument;
        }
        return null;
    }

    public hasPhoto(): boolean {
        return (this.message.photo ?? []).length > 0;
    }

    public isSupported(): boolean {
        return this.message.text !== undefined || this.hasPhoto() || this.imageDocumentFileId() !== null;
    }

    private photoFileId(): string | null {
        const photo = [...(this.message.photo ?? [])].sort((left, right) => (right.file_size ?? 0) - (left.file_size ?? 0))[0];
        return photo?.file_id ? stringValue(photo.file_id) : null;
    }

    private imageDocumentFileId(): string | null {
        const document = this.message.document;
        if (!document?.file_id || !this.isImageDocument(document)) {
            return null;
        }
        return stringValue(document.file_id);
    }

    private isImageDocument(document: TelegramDocument): boolean {
        const mimeType = stringValue(document.mime_type).toLowerCase();
        if (DOCUMENT_CONSTANTS.imageMimeTypePrefixes.some(prefix => mimeType.startsWith(prefix))) {
            return true;
        }
        const fileName = stringValue(document.file_name).toLowerCase();
        return DOCUMENT_CONSTANTS.imageExtensions.some(extension => fileName.endsWith(extension));
    }
}

@Injectable()
export class TelegramUpdateParserService {
    public parseIncomingTelegramMessage(update: TelegramUpdate | null | undefined): InboundMessage | null {
        const message = update?.message;
        if (!this.isValidUpdate(update, message)) {
            return null;
        }
        const extractor = new MessageExtractor(message!);
        if (!extractor.isSupported()) {
            return null;
        }
        const reply = extractor.getReplyToMessage();
        return new InboundMessage({
            chatId: extractor.getChatId(),
            imageFileIds: extractor.getImageFileIds(),
            mediaGroupId: extractor.getMediaGroupId(),
            messageId: extractor.getMessageId(),
            processingUpdates: [],
            replyToImageFileIds: reply?.getImageFileIds() ?? [],
            replyToText: extractor.getReplyToText(),
            text: extractor.getText(),
            userId: extractor.getUserId(),
            updateId: update!.update_id!,
        });
    }

    private isValidUpdate(update: TelegramUpdate | null | undefined, message?: TelegramMessage): boolean {
        return update?.update_id !== undefined && message?.message_id !== undefined && message.from !== undefined && message.chat !== undefined;
    }
}
