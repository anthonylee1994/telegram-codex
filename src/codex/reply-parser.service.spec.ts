import {JsonPayloadParserService} from "./json-payload-parser.service";
import {ReplyParserService} from "./reply-parser.service";

describe("ReplyParserService", () => {
    const parser = new ReplyParserService(new JsonPayloadParserService());

    it("parses strict JSON replies", () => {
        expect(parser.parseReply('{"text":"ok","suggested_replies":["a","b","c"]}')).toEqual({text: "ok", suggestedReplies: ["a", "b", "c"]});
    });

    it("falls back to default suggested replies", () => {
        expect(parser.parseReply("plain text").text).toBe("plain text");
        expect(parser.parseReply("plain text").suggestedReplies).toHaveLength(3);
    });
});
