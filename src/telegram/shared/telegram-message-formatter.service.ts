import {Injectable, Optional} from "@nestjs/common";
import {ReplyParserService} from "../../codex/parsing/reply-parser.service";
import {TELEGRAM_CONSTANTS} from "./telegram.types";

export interface TelegramReplyMarkup {
    remove_keyboard?: boolean;
    keyboard?: {text: string}[][];
    resize_keyboard?: boolean;
    one_time_keyboard?: boolean;
}

function escapeHtml(text: string | null | undefined): string {
    return (text ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function stripSingleLeadingNewline(text: string): string {
    if (text.startsWith("\r\n")) {
        return text.substring(2);
    }
    if (text.startsWith("\n")) {
        return text.substring(1);
    }
    return text;
}

@Injectable()
export class TelegramMessageFormatterService {
    public constructor(@Optional() private readonly replyParser?: ReplyParserService) {}

    public formatForTelegram(text: string | null | undefined): string {
        if (!text) {
            return "";
        }
        const pattern = /```(?:[\t ]*[\w#+.-]+)?\n?([\s\S]*?)```/g;
        let cursor = 0;
        let formatted = "";
        for (const match of text.matchAll(pattern)) {
            formatted += this.formatInlineSegment(text.substring(cursor, match.index));
            formatted += `<pre><code>${escapeHtml(stripSingleLeadingNewline(match[1] ?? ""))}</code></pre>`;
            cursor = (match.index ?? 0) + match[0].length;
        }
        formatted += this.formatInlineSegment(text.substring(cursor));
        return formatted;
    }

    public buildReplyMarkup(suggestedReplies: string[] | null | undefined, removeKeyboard: boolean): TelegramReplyMarkup | null {
        if (removeKeyboard) {
            return {remove_keyboard: true};
        }
        const replies = this.cleanReplies(suggestedReplies ?? []);
        if (replies.length === 0) {
            return null;
        }
        return {
            keyboard: replies.map(reply => [{text: reply}]),
            resize_keyboard: true,
            one_time_keyboard: true,
        };
    }

    public normalizeReply(text: string | null | undefined, suggestedReplies: string[] | null | undefined): {text: string; suggestedReplies: string[]} {
        const payload = this.replyParser?.parseReply(text) ?? {text: text ?? "", suggestedReplies: []};
        return {
            text: payload.text.trim() ? payload.text : (text ?? ""),
            suggestedReplies: suggestedReplies && suggestedReplies.length > 0 ? suggestedReplies : payload.suggestedReplies,
        };
    }

    private cleanReplies(replies: string[]): string[] {
        const cleaned: string[] = [];
        for (const reply of replies) {
            const normalized = reply.trim();
            if (normalized && !cleaned.includes(normalized)) {
                cleaned.push(normalized);
            }
            if (cleaned.length === TELEGRAM_CONSTANTS.maxSuggestedReplies) {
                break;
            }
        }
        return cleaned;
    }

    private formatInlineSegment(text: string): string {
        return this.applyRules(text, 0);
    }

    private applyRules(text: string, ruleIndex: number): string {
        const rules: Array<[RegExp, (match: RegExpExecArray) => string]> = [
            [/`([^`\n]+)`/g, match => this.wrap("code", match[1])],
            [/\*\*([^*\n]+)\*\*/g, match => this.wrap("b", match[1])],
            [/\|\|([^|\n]+)\|\|/g, match => this.wrap("tg-spoiler", match[1])],
            [/~~([^~\n]+)~~/g, match => this.wrap("s", match[1])],
            [/(?<!_)__([^_\n]+)__(?!_)/g, match => this.wrap("i", match[1])],
        ];
        if (ruleIndex >= rules.length) {
            return escapeHtml(text);
        }
        const [pattern, replacement] = rules[ruleIndex];
        let formatted = "";
        let cursor = 0;
        pattern.lastIndex = 0;
        let match = pattern.exec(text);
        while (match) {
            formatted += this.applyRules(text.substring(cursor, match.index), ruleIndex + 1);
            formatted += replacement(match);
            cursor = match.index + match[0].length;
            match = pattern.exec(text);
        }
        formatted += this.applyRules(text.substring(cursor), ruleIndex + 1);
        return formatted;
    }

    private wrap(tag: string, content: string): string {
        return `<${tag}>${escapeHtml(content)}</${tag}>`;
    }
}
