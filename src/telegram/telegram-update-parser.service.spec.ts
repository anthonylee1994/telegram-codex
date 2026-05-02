import {TelegramUpdateParserService} from "./telegram-update-parser.service";
import {MESSAGE_CONSTANTS} from "./message-constants";

describe("TelegramUpdateParserService", () => {
    const parser = new TelegramUpdateParserService();

    it("parses text messages", () => {
        const message = parser.parseIncomingTelegramMessage({
            update_id: 99,
            message: {message_id: 10, from: {id: 5}, chat: {id: 3}, text: "hello"},
        });

        expect(message?.chatId).toBe("3");
        expect(message?.userId).toBe("5");
        expect(message?.text).toBe("hello");
    });

    it("uses largest photo and caption", () => {
        const message = parser.parseIncomingTelegramMessage({
            update_id: 99,
            message: {
                message_id: 10,
                from: {id: 5},
                chat: {id: 3},
                caption: "cap",
                photo: [
                    {file_id: "small", file_size: 1},
                    {file_id: "large", file_size: 9},
                ],
            },
        });

        expect(message?.imageFileIds).toEqual(["large"]);
        expect(message?.text).toBe("cap");
    });

    it("parses reply-to image context", () => {
        const message = parser.parseIncomingTelegramMessage({
            update_id: 99,
            message: {
                message_id: 10,
                from: {id: 5},
                chat: {id: 3},
                text: "reply",
                reply_to_message: {
                    message_id: 8,
                    from: {id: 5},
                    chat: {id: 3},
                    photo: [{file_id: "photo", file_size: 1}],
                },
            },
        });

        expect(message?.replyToText).toBe(MESSAGE_CONSTANTS.replyToImage);
        expect(message?.replyToImageFileIds).toEqual(["photo"]);
    });
});
