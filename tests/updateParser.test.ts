import {describe, expect, it} from "vitest";

import {parseIncomingTelegramMessage} from "../src/telegram/updateParser.js";

describe("parseIncomingTelegramMessage", () => {
    it("parses sender user id from Telegram message", () => {
        const parsed = parseIncomingTelegramMessage({
            update_id: 1,
            message: {
                from: {
                    id: 234392020,
                },
                message_id: 2,
                text: "hello",
                chat: {
                    id: 3,
                },
            },
        });

        expect(parsed).toEqual({
            chatId: "3",
            messageId: 2,
            text: "hello",
            userId: "234392020",
            updateId: 1,
        });
    });
});
