import {Injectable} from "@nestjs/common";
import {MESSAGE_CONSTANTS} from "../telegram/message-constants";
import {TELEGRAM_CONSTANTS} from "../telegram/telegram.types";
import {JsonPayloadParserService, normalizeText} from "./json-payload-parser.service";

export interface ParsedReply {
    text: string;
    suggestedReplies: string[];
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value);
}

@Injectable()
export class ReplyParserService {
    public constructor(private readonly jsonPayloadParser: JsonPayloadParserService) {}

    public parseReply(rawReply: string | null | undefined): ParsedReply {
        try {
            const payload = this.jsonPayloadParser.parsePayload(rawReply);
            return {
                text: this.extractReplyText(payload, rawReply),
                suggestedReplies: this.extractSuggestedReplies(payload, rawReply),
            };
        } catch {
            return {
                text: this.fallbackReplyText(rawReply),
                suggestedReplies: this.sanitizeSuggestedReplies([rawReply], MESSAGE_CONSTANTS.defaultSuggestedReplies),
            };
        }
    }

    public sanitizeSuggestedReplies(replies: unknown[] | null | undefined, fallback: string[]): string[] {
        const cleaned: string[] = [];
        for (const reply of replies ?? []) {
            if (typeof reply !== "string") {
                continue;
            }
            const normalized = reply.trim().replace(/\s+/g, " ");
            if (normalized && !cleaned.includes(normalized)) {
                cleaned.push(normalized.length > TELEGRAM_CONSTANTS.maxSuggestedReplyLength ? normalized.substring(0, TELEGRAM_CONSTANTS.maxSuggestedReplyLength) : normalized);
            }
            if (cleaned.length === TELEGRAM_CONSTANTS.maxSuggestedReplies) {
                break;
            }
        }
        return cleaned.length < TELEGRAM_CONSTANTS.maxSuggestedReplies ? fallback : cleaned;
    }

    private extractReplyText(payload: unknown, rawReply: string | null | undefined): string {
        if (isRecord(payload)) {
            const text = payload.text;
            if (typeof text === "string" && text.trim()) {
                return normalizeText(text);
            }
            const candidate = Object.values(payload)
                .filter((value): value is string => typeof value === "string" && value.trim().length > 0)
                .sort()
                .at(-1);
            if (candidate) {
                return normalizeText(candidate);
            }
        }
        if (typeof payload === "string" && payload.trim()) {
            return normalizeText(payload);
        }
        return this.fallbackReplyText(rawReply);
    }

    private fallbackReplyText(rawReply: string | null | undefined): string {
        const normalized = normalizeText(rawReply ?? "");
        if (!normalized) {
            throw new Error("codex exec returned an empty reply");
        }
        return normalized;
    }

    private extractSuggestedReplies(payload: unknown, rawReply: string | null | undefined): string[] {
        if (Array.isArray(payload)) {
            return this.sanitizeSuggestedReplies(payload, MESSAGE_CONSTANTS.defaultSuggestedReplies);
        }
        if (isRecord(payload) && Array.isArray(payload.suggested_replies)) {
            return this.sanitizeSuggestedReplies(payload.suggested_replies, MESSAGE_CONSTANTS.defaultSuggestedReplies);
        }
        return this.sanitizeSuggestedReplies([rawReply], MESSAGE_CONSTANTS.defaultSuggestedReplies);
    }
}
