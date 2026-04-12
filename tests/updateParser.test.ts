import {describe, expect, it} from "vitest";

import {TelegramUpdateParser} from "../src/telegram/telegram-update-parser.service.js";

describe("TelegramUpdateParser", () => {
    const parser = new TelegramUpdateParser();

    it("parses sender user id from Telegram message", () => {
        const parsed = parser.parseIncomingTelegramMessage({
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
            imageFileId: null,
            messageId: 2,
            text: "hello",
            userId: "234392020",
            updateId: 1,
        });
    });

    it("parses Telegram photo message with caption", () => {
        const parsed = parser.parseIncomingTelegramMessage({
            update_id: 1,
            message: {
                from: {
                    id: 234392020,
                },
                message_id: 2,
                caption: "睇下呢張圖",
                photo: [
                    {
                        file_id: "small-file",
                        file_size: 100,
                    },
                    {
                        file_id: "large-file",
                        file_size: 200,
                    },
                ],
                chat: {
                    id: 3,
                },
            },
        });

        expect(parsed).toEqual({
            chatId: "3",
            imageFileId: "large-file",
            messageId: 2,
            text: "睇下呢張圖",
            userId: "234392020",
            updateId: 1,
        });
    });
});
