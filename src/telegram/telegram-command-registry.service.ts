import {Injectable} from "@nestjs/common";
import {InboundMessage} from "./inbound-message";

export type TelegramCommand = "START" | "NEW_SESSION" | "HELP" | "STATUS" | "SESSION" | "MEMORY" | "FORGET" | "COMPACT";

@Injectable()
export class TelegramCommandRegistryService {
    private readonly commands: Array<{pattern: RegExp; command: TelegramCommand}> = [
        {pattern: /^\/start(?:@[\w_]+)?$/u, command: "START"},
        {pattern: /^\/new(?:@[\w_]+)?$/u, command: "NEW_SESSION"},
        {pattern: /^\/help(?:@[\w_]+)?$/u, command: "HELP"},
        {pattern: /^\/status(?:@[\w_]+)?$/u, command: "STATUS"},
        {pattern: /^\/session(?:@[\w_]+)?$/u, command: "SESSION"},
        {pattern: /^\/memory(?:@[\w_]+)?$/u, command: "MEMORY"},
        {pattern: /^\/forget(?:@[\w_]+)?$/u, command: "FORGET"},
        {pattern: /^\/compact(?:@[\w_]+)?$/u, command: "COMPACT"},
    ];

    public resolve(message: InboundMessage): TelegramCommand | null {
        return this.commands.find(entry => entry.pattern.test(message.textOrEmpty()))?.command ?? null;
    }
}
