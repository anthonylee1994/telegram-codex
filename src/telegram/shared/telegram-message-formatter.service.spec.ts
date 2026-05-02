import {TelegramMessageFormatterService} from "./telegram-message-formatter.service";

describe("TelegramMessageFormatterService", () => {
    const formatter = new TelegramMessageFormatterService();

    it("escapes html and formats inline markdown", () => {
        expect(formatter.formatForTelegram("**hi** <x> `code`")).toBe("<b>hi</b> &lt;x&gt; <code>code</code>");
    });

    it("formats fenced code blocks", () => {
        expect(formatter.formatForTelegram("```ts\nconst a = 1 < 2\n```")).toBe("<pre><code>const a = 1 &lt; 2\n</code></pre>");
    });

    it("cleans suggested replies", () => {
        expect(formatter.buildReplyMarkup([" a ", "a", "b"], false)).toEqual({
            keyboard: [[{text: "a"}], [{text: "b"}]],
            resize_keyboard: true,
            one_time_keyboard: true,
        });
    });
});
