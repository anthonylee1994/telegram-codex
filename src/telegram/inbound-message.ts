function blankToNull(value: string | null | undefined): string | null {
    if (value === null || value === undefined) {
        return null;
    }
    const normalized = value.trim();
    return normalized.length === 0 ? null : normalized;
}

function normalizeStrings(values: string[] | null | undefined): string[] {
    return [...new Set((values ?? []).map(value => blankToNull(value)).filter(Boolean) as string[])];
}

export interface ProcessingUpdate {
    update_id: number;
    message_id: number;
}

export class InboundMessage {
    public readonly chatId: string;
    public readonly imageFileIds: string[];
    public readonly mediaGroupId: string | null;
    public readonly messageId: number;
    public readonly processingUpdates: ProcessingUpdate[];
    public readonly replyToImageFileIds: string[];
    public readonly replyToText: string | null;
    public readonly text: string | null;
    public readonly userId: string;
    public readonly updateId: number;

    public constructor(params: {
        chatId: string;
        imageFileIds?: string[];
        mediaGroupId?: string | null;
        messageId: number;
        processingUpdates?: ProcessingUpdate[];
        replyToImageFileIds?: string[];
        replyToText?: string | null;
        text?: string | null;
        userId: string;
        updateId: number;
    }) {
        this.chatId = params.chatId;
        this.imageFileIds = normalizeStrings(params.imageFileIds);
        this.mediaGroupId = blankToNull(params.mediaGroupId);
        this.messageId = params.messageId;
        this.replyToImageFileIds = normalizeStrings(params.replyToImageFileIds);
        this.replyToText = blankToNull(params.replyToText);
        this.text = blankToNull(params.text);
        this.userId = params.userId;
        this.updateId = params.updateId;
        this.processingUpdates = (params.processingUpdates?.length ? params.processingUpdates : [{update_id: params.updateId, message_id: params.messageId}]).sort(
            (left, right) => left.message_id - right.message_id || left.update_id - right.update_id
        );
    }

    public static forMergedMediaGroup(primary: InboundMessage, imageFileIds: string[], processingUpdates: ProcessingUpdate[], text: string | null): InboundMessage {
        return new InboundMessage({
            chatId: primary.chatId,
            imageFileIds,
            mediaGroupId: primary.mediaGroupId,
            messageId: primary.messageId,
            processingUpdates,
            replyToImageFileIds: [],
            replyToText: null,
            text,
            userId: primary.userId,
            updateId: primary.updateId,
        });
    }

    public mediaGroup(): boolean {
        return this.mediaGroupId !== null;
    }

    public unsupported(): boolean {
        return !this.text && this.imageFileIds.length === 0;
    }

    public imageCount(): number {
        return this.imageFileIds.length;
    }

    public textOrEmpty(): string {
        return this.text ?? "";
    }

    public effectiveImageFileIds(): string[] {
        return this.imageFileIds.length > 0 ? this.imageFileIds : this.replyToImageFileIds;
    }

    public toJSON(): Record<string, unknown> {
        return {
            chatId: this.chatId,
            imageFileIds: this.imageFileIds,
            mediaGroupId: this.mediaGroupId,
            messageId: this.messageId,
            processingUpdates: this.processingUpdates,
            replyToImageFileIds: this.replyToImageFileIds,
            replyToText: this.replyToText,
            text: this.text,
            userId: this.userId,
            updateId: this.updateId,
        };
    }

    public static fromJSON(payload: unknown): InboundMessage {
        const value = payload as Partial<InboundMessage> & {
            processingUpdates?: ProcessingUpdate[];
        };
        return new InboundMessage({
            chatId: String(value.chatId ?? ""),
            imageFileIds: value.imageFileIds ?? [],
            mediaGroupId: value.mediaGroupId ?? null,
            messageId: Number(value.messageId ?? 0),
            processingUpdates: value.processingUpdates ?? [],
            replyToImageFileIds: value.replyToImageFileIds ?? [],
            replyToText: value.replyToText ?? null,
            text: value.text ?? null,
            userId: String(value.userId ?? ""),
            updateId: Number(value.updateId ?? 0),
        });
    }
}
