import {Injectable} from "@nestjs/common";

@Injectable()
export class JsonPayloadParserService {
    public parsePayload(rawReply: string | null | undefined): unknown {
        for (const candidate of this.candidatePayloads(rawReply)) {
            if (!candidate.trim()) {
                continue;
            }
            try {
                let payload = JSON.parse(candidate) as unknown;
                if (typeof payload === "string") {
                    payload = JSON.parse(payload) as unknown;
                }
                if (payload !== null) {
                    return payload;
                }
            } catch {
                // Keep trying relaxed candidates.
            }
        }
        throw new Error("Reply payload is not JSON");
    }

    private candidatePayloads(rawReply: string | null | undefined): string[] {
        const normalized = (rawReply ?? "").trim();
        const unwrapped = normalized
            .replace(/^```(?:json)?\s*/, "")
            .replace(/\s*```$/, "")
            .trim();
        const extracted = this.extractJsonObject(unwrapped);
        const relaxed = this.extractRelaxedPayload(extracted ?? unwrapped);
        return [normalized, unwrapped, extracted ?? "", relaxed ?? ""];
    }

    private extractJsonObject(text: string): string | null {
        const start = text.indexOf("{");
        const end = text.lastIndexOf("}");
        return start >= 0 && start < end ? text.substring(start, end + 1) : null;
    }

    private extractRelaxedPayload(text: string): string | null {
        const replyText = this.extractRelaxedText(text);
        const suggestedReplies = this.extractRelaxedSuggestedReplies(text);
        if (!replyText && suggestedReplies.length === 0) {
            return null;
        }
        return JSON.stringify({text: replyText, suggested_replies: suggestedReplies});
    }

    private extractRelaxedText(text: string): string {
        const match = /"text"\s*:\s*"(?<value>[\s\S]*?)"\s*,\s*"suggested_replies"\s*:/.exec(text);
        return normalizeText(match?.groups?.value);
    }

    private extractRelaxedSuggestedReplies(text: string): string[] {
        const match = /"suggested_replies"\s*:\s*\[(?<value>[\s\S]*?)]/.exec(text);
        if (!match?.groups?.value) {
            return [];
        }
        return [...match.groups.value.matchAll(/"((?:\\.|[^"\\]|[\r\n])*)"/g)].map(reply => normalizeText(reply[1]));
    }
}

export function normalizeText(value: string | null | undefined): string {
    return (value ?? "")
        .replace(/\\r\\n/g, "\n")
        .replace(/\\n/g, "\n")
        .replace(/\\t/g, "\t")
        .trim();
}
