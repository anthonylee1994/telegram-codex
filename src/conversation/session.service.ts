import {Injectable} from "@nestjs/common";
import {CodexSessionCompactClientService} from "../codex/codex-session-compact-client.service";
import {Transcript} from "../codex/transcript";
import {MESSAGE_CONSTANTS} from "../telegram/message-constants";
import {ChatMemoryRepository} from "./chat-memory.repository";
import {ChatSessionRepository} from "./chat-session.repository";
import {CONVERSATION_CONSTANTS, formatConversationTime} from "./conversation.constants";

export interface SessionSnapshot {
    active: boolean;
    messageCount: number;
    turnCount: number;
    lastUpdatedAt: string | null;
}

export interface SessionCompactResult {
    status: "MISSING_SESSION" | "TOO_SHORT" | "OK";
    messageCount: number | null;
    originalMessageCount: number | null;
    compactText: string | null;
}

export interface MemorySnapshot {
    active: boolean;
    memoryText: string | null;
    lastUpdatedAt: string | null;
}

@Injectable()
export class SessionService {
    public constructor(
        private readonly chatSessionRepository: ChatSessionRepository,
        private readonly sessionCompactClient: CodexSessionCompactClientService,
        private readonly chatMemoryRepository: ChatMemoryRepository
    ) {}

    public async persistConversationState(chatId: string, conversationState: string | null | undefined): Promise<void> {
        await this.chatSessionRepository.persist(chatId, conversationState);
    }

    public async reset(chatId: string): Promise<void> {
        await this.chatSessionRepository.reset(chatId);
    }

    public async snapshot(chatId: string): Promise<SessionSnapshot> {
        const session = await this.chatSessionRepository.findActive(chatId);
        if (!session) {
            return {active: false, messageCount: 0, turnCount: 0, lastUpdatedAt: null};
        }
        const transcript = Transcript.fromConversationState(session.lastResponseId);
        return {
            active: true,
            messageCount: transcript.size(),
            turnCount: Math.ceil(transcript.size() / 2),
            lastUpdatedAt: formatConversationTime(session.updatedAt),
        };
    }

    public async compact(chatId: string): Promise<SessionCompactResult> {
        const session = await this.chatSessionRepository.findActive(chatId);
        if (!session) {
            return {status: "MISSING_SESSION", messageCount: null, originalMessageCount: null, compactText: null};
        }
        const transcript = Transcript.fromConversationState(session.lastResponseId);
        if (transcript.size() < CONVERSATION_CONSTANTS.minTranscriptSizeForCompact) {
            return {
                status: "TOO_SHORT",
                messageCount: transcript.size(),
                originalMessageCount: null,
                compactText: null,
            };
        }
        const compactText = await this.sessionCompactClient.compact(transcript);
        await this.chatSessionRepository.persist(chatId, Transcript.empty().append("user", MESSAGE_CONSTANTS.compactBaselineMessage).append("assistant", compactText).toConversationState());
        return {
            status: "OK",
            messageCount: null,
            originalMessageCount: transcript.size(),
            compactText,
        };
    }

    public async memorySnapshot(chatId: string): Promise<MemorySnapshot> {
        const memory = await this.chatMemoryRepository.find(chatId);
        if (!memory?.memoryText?.trim()) {
            return {active: false, memoryText: null, lastUpdatedAt: null};
        }
        return {
            active: true,
            memoryText: memory.memoryText,
            lastUpdatedAt: formatConversationTime(memory.updatedAt),
        };
    }

    public async resetMemory(chatId: string): Promise<void> {
        await this.chatMemoryRepository.reset(chatId);
    }
}
