import {Injectable} from "@nestjs/common";
import {ConfigService} from "@nestjs/config";
import {AppConfig} from "./app-config";

@Injectable()
export class AppConfigService {
    public constructor(private readonly configService: ConfigService<AppConfig, true>) {}

    public get port(): number {
        return this.configService.get("port", {infer: true});
    }

    public get baseUrl(): string {
        return this.configService.get("baseUrl", {infer: true});
    }

    public get telegramBotToken(): string {
        return this.configService.get("telegramBotToken", {infer: true});
    }

    public get telegramWebhookSecret(): string {
        return this.configService.get("telegramWebhookSecret", {infer: true});
    }

    public get allowedTelegramUserIds(): string[] {
        return this.configService.get("allowedTelegramUserIds", {infer: true});
    }

    public get sqliteDbPath(): string {
        return this.configService.get("sqliteDbPath", {infer: true});
    }

    public get codexExecTimeoutSeconds(): number {
        return this.configService.get("codexExecTimeoutSeconds", {infer: true});
    }

    public get maxMediaGroupImages(): number {
        return this.configService.get("maxMediaGroupImages", {infer: true});
    }

    public get sessionTtlDays(): number {
        return this.configService.get("sessionTtlDays", {infer: true});
    }

    public get mediaGroupWaitMs(): number {
        return this.configService.get("mediaGroupWaitMs", {infer: true});
    }

    public get rateLimitWindowMs(): number {
        return this.configService.get("rateLimitWindowMs", {infer: true});
    }

    public get rateLimitMaxMessages(): number {
        return this.configService.get("rateLimitMaxMessages", {infer: true});
    }

    public get codexSandboxMode(): string {
        return this.configService.get("codexSandboxMode", {infer: true});
    }
}
