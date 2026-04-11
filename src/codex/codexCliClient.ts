import {spawn} from "node:child_process";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";

import {SYSTEM_PROMPT} from "../conversation/prompts.js";
import type {GenerateReplyInput, GenerateReplyResult} from "../types/conversation.js";
import type {ReplyClient} from "../types/services.js";

interface TranscriptMessage {
    role: "user" | "assistant";
    content: string;
}

const MAX_TRANSCRIPT_MESSAGES = 30;

export class CodexCliClient implements ReplyClient {
    public async generateReply(input: GenerateReplyInput): Promise<GenerateReplyResult> {
        const transcript = parseConversationState(input.conversationState);
        const userMessage = input.text || input.imageFilePath ? input.text || "請描述呢張圖，並按我需要幫我分析。" : "";
        const nextTranscript = trimTranscript([...transcript, {role: "user", content: userMessage}]);
        const prompt = buildPrompt(nextTranscript, Boolean(input.imageFilePath));
        const text = await runCodexExec(prompt, input.imageFilePath ?? null);
        const updatedTranscript = trimTranscript([...nextTranscript, {role: "assistant", content: text}]);

        return {
            conversationState: JSON.stringify(updatedTranscript),
            text,
        };
    }
}

function parseConversationState(conversationState: string | null): TranscriptMessage[] {
    if (!conversationState) {
        return [];
    }

    try {
        const parsed = JSON.parse(conversationState) as unknown;

        if (!Array.isArray(parsed)) {
            return [];
        }

        return parsed.flatMap(function mapTranscriptMessage(message: unknown): TranscriptMessage[] {
            if (!message || typeof message !== "object") {
                return [];
            }

            const role = "role" in message ? message.role : undefined;
            const content = "content" in message ? message.content : undefined;

            if ((role !== "user" && role !== "assistant") || typeof content !== "string" || !content.trim()) {
                return [];
            }

            return [{role, content}];
        });
    } catch {
        return [];
    }
}

function trimTranscript(transcript: TranscriptMessage[]): TranscriptMessage[] {
    return transcript.slice(-MAX_TRANSCRIPT_MESSAGES);
}

function buildPrompt(transcript: TranscriptMessage[], hasImage: boolean): string {
    const lines = transcript.map(function formatTranscriptMessage(message: TranscriptMessage, index: number): string {
        return `${index + 1}. ${message.role}: ${message.content}`;
    });

    return [
        SYSTEM_PROMPT,
        "",
        hasImage ? "The latest user message includes an attached image." : "",
        "Conversation so far:",
        ...lines,
        "",
        "Reply only with the assistant message for the latest user input.",
    ]
        .filter(Boolean)
        .join("\n");
}

async function runCodexExec(prompt: string, imageFilePath: string | null): Promise<string> {
    const tempDir = await fs.mkdtemp(path.join(os.tmpdir(), "telegram-codex-"));
    const outputPath = path.join(tempDir, "reply.txt");

    try {
        const stderrChunks: string[] = [];

        await new Promise<void>(function execute(resolve, reject) {
            const args = ["exec", "--skip-git-repo-check", "--dangerously-bypass-approvals-and-sandbox", "--color", "never", "--output-last-message", outputPath];

            if (imageFilePath) {
                args.push("--image", imageFilePath);
            }

            args.push("-");

            const child = spawn("codex", args, {
                cwd: process.cwd(),
                stdio: ["pipe", "ignore", "pipe"],
            });

            child.stdin.end(prompt);
            child.stderr.setEncoding("utf8");
            child.stderr.on("data", function onStderr(chunk: string) {
                stderrChunks.push(chunk);
            });
            child.on("error", reject);
            child.on("close", function onClose(code) {
                if (code === 0) {
                    resolve();
                    return;
                }

                reject(new Error(`codex exec failed with exit code ${code}: ${stderrChunks.join("").trim() || "unknown error"}`));
            });
        });

        const text = (await fs.readFile(outputPath, "utf8")).trim();

        if (!text) {
            throw new Error("codex exec returned an empty reply");
        }

        return text;
    } finally {
        await fs.rm(tempDir, {recursive: true, force: true});
    }
}
