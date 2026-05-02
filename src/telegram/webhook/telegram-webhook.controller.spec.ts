import {HttpException} from "@nestjs/common";
import {AppConfigService} from "../../config/app-config.service";
import {TelegramWebhookController} from "./telegram-webhook.controller";
import {TelegramWebhookService} from "./telegram-webhook.service";

describe("TelegramWebhookController", () => {
    function config(): AppConfigService {
        return {telegramWebhookSecret: "secret"} as AppConfigService;
    }

    it("rejects invalid secret", async () => {
        const controller = new TelegramWebhookController(config(), {} as TelegramWebhookService);

        await expect(controller.create("bad", {update_id: 1})).rejects.toBeInstanceOf(HttpException);
    });

    it("accepts valid secret", async () => {
        const handler = {handle: jest.fn().mockResolvedValue(undefined)} as unknown as TelegramWebhookService;
        const controller = new TelegramWebhookController(config(), handler);
        const payload = {update_id: 1};

        await expect(controller.create("secret", payload)).resolves.toEqual({ok: true});
        expect(handler.handle).toHaveBeenCalledWith(payload);
    });
});
