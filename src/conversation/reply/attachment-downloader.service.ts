import * as fs from "node:fs/promises";
import * as path from "node:path";
import {Inject, Injectable, Logger} from "@nestjs/common";
import {TELEGRAM_GATEWAY} from "../../telegram/shared/telegram.types";
import type {TelegramGateway} from "../../telegram/shared/telegram.types";

@Injectable()
export class AttachmentDownloaderService {
    private readonly logger = new Logger(AttachmentDownloaderService.name);

    public constructor(@Inject(TELEGRAM_GATEWAY) private readonly telegramClient: TelegramGateway) {}

    public async downloadImages(imageFileIds: string[]): Promise<string[]> {
        return Promise.all(imageFileIds.map(fileId => this.telegramClient.downloadFileToTemp(fileId)));
    }

    public async cleanup(filePaths: Array<string | null | undefined>): Promise<void> {
        const directories = new Set(filePaths.filter((filePath): filePath is string => Boolean(filePath)).map(filePath => path.dirname(filePath)));
        for (const directory of directories) {
            await fs.rm(directory, {recursive: true, force: true}).catch((error: unknown) => {
                this.logger.warn(`Failed to delete directory: ${directory}`, error as Error);
            });
        }
    }
}
