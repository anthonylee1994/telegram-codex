import {MESSAGE_CONSTANTS} from "../../telegram/shared/message-constants";

export interface TranscriptEntry {
    role: string;
    content: string;
}

export class Transcript {
    private static readonly maxTranscriptMessages = 100;
    private readonly messages: TranscriptEntry[];

    private constructor(messages: TranscriptEntry[]) {
        this.messages = messages.length <= Transcript.maxTranscriptMessages ? [...messages] : messages.slice(messages.length - Transcript.maxTranscriptMessages);
    }

    public static empty(): Transcript {
        return new Transcript([]);
    }

    public static fromConversationState(conversationState: string | null | undefined): Transcript {
        if (!conversationState?.trim()) {
            return Transcript.empty();
        }
        try {
            const payload = JSON.parse(conversationState) as TranscriptEntry[];
            return new Transcript(
                payload
                    .filter(entry => ["user", "assistant"].includes(entry.role))
                    .map(entry => ({role: entry.role, content: entry.content}))
                    .filter(entry => entry.content.trim())
            );
        } catch {
            return Transcript.empty();
        }
    }

    public append(role: string | null | undefined, content: string | null | undefined): Transcript {
        return new Transcript([...this.messages, {role: role ?? "", content: content ?? ""}]);
    }

    public size(): number {
        return this.messages.length;
    }

    public toTaggedPromptLines(): string[] {
        return this.messages.flatMap((message, index) => [`<message index="${index + 1}" role="${message.role}">`, message.content, "</message>"]);
    }

    public toConversationState(): string {
        return JSON.stringify(this.messages);
    }

    public static compactBaseline(compactText: string): Transcript {
        return Transcript.empty().append("user", MESSAGE_CONSTANTS.compactBaselineMessage).append("assistant", compactText);
    }
}
