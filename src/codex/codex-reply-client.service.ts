import {Injectable} from "@nestjs/common";
import {ReplyResult} from "../conversation/reply-result";
import {TELEGRAM_CONSTANTS} from "../telegram/telegram.types";
import {ExecRunnerService} from "./exec-runner.service";
import {PromptBuilderService} from "./prompt-builder.service";
import {ReplyParserService} from "./reply-parser.service";
import {Transcript} from "./transcript";

@Injectable()
export class CodexReplyClientService {
    public constructor(
        private readonly execRunner: ExecRunnerService,
        private readonly promptBuilder: PromptBuilderService,
        private readonly replyParser: ReplyParserService
    ) {}

    public async generateReply(
        userMessage: string | null | undefined,
        conversationState: string | null | undefined,
        imageFilePaths: string[],
        replyToText: string | null | undefined,
        longTermMemory: string | null | undefined
    ): Promise<ReplyResult> {
        const nextTranscript = this.appendUserMessage(conversationState, userMessage, imageFilePaths, replyToText);
        const rawReply = await this.execRunner.run(
            this.promptBuilder.buildReplySystemPrompt(),
            this.promptBuilder.buildReplyUserPrompt(nextTranscript, imageFilePaths.length > 0, imageFilePaths.length, longTermMemory),
            imageFilePaths,
            this.replyOutputSchema()
        );
        const parsedReply = this.replyParser.parseReply(rawReply);
        return {
            conversationState: nextTranscript.append("assistant", parsedReply.text).toConversationState(),
            suggestedReplies: parsedReply.suggestedReplies,
            text: parsedReply.text,
        };
    }

    private appendUserMessage(conversationState: string | null | undefined, text: string | null | undefined, imageFilePaths: string[], replyToText: string | null | undefined): Transcript {
        return Transcript.fromConversationState(conversationState).append("user", this.buildUserMessage(text, imageFilePaths, replyToText));
    }

    private buildUserMessage(text: string | null | undefined, imageFilePaths: string[], replyToText: string | null | undefined): string {
        const baseText = this.normalizeUserText(text, imageFilePaths);
        if (!replyToText?.trim()) {
            return baseText;
        }
        return ["你而家係回覆緊之前一則訊息。", `被引用訊息：${replyToText}`, `你今次嘅新訊息：${baseText.trim() ? baseText : "（冇文字）"}`].join("\n");
    }

    private normalizeUserText(text: string | null | undefined, imageFilePaths: string[]): string {
        const baseText = text ?? "";
        if (baseText.trim() || imageFilePaths.length === 0) {
            return baseText;
        }
        if (imageFilePaths.length === 1) {
            return "我上載咗張圖。請先描述圖片，再按內容幫我分析重點。";
        }
        const labels = Array.from({length: imageFilePaths.length}, (_, index) => `圖 ${index + 1}`).join("、");
        return `我上載咗 ${imageFilePaths.length} 張圖。請按 ${labels} 逐張描述，再比較異同同整理重點。`;
    }

    private replyOutputSchema(): unknown {
        return {
            type: "object",
            additionalProperties: false,
            required: ["text", "suggested_replies"],
            properties: {
                text: {type: "string", minLength: 1},
                suggested_replies: {
                    type: "array",
                    minItems: TELEGRAM_CONSTANTS.maxSuggestedReplies,
                    maxItems: TELEGRAM_CONSTANTS.maxSuggestedReplies,
                    items: {type: "string", minLength: 1},
                },
            },
        };
    }
}
