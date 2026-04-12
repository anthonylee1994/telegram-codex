import {ConsoleLogger, Injectable} from "@nestjs/common";
import type {LoggerService, LogLevel} from "@nestjs/common";
import type {Logger} from "./service.types.js";

@Injectable()
export class AppLogger implements Logger, LoggerService {
    private readonly logger = new ConsoleLogger("telegram-codex", {
        json: false,
        colors: true,
        compact: true,
        forceConsole: true,
    });

    public constructor() {
        this.logger.setLogLevels(["debug", "error", "fatal", "log", "verbose", "warn"]);
    }

    public info(message: string, context?: Record<string, unknown>): void {
        this.write("log", message, context);
    }

    public warn(message: string, context?: Record<string, unknown>): void {
        this.write("warn", message, context);
    }

    public error(message: string, context?: Record<string, unknown>): void {
        this.write("error", message, context);
    }

    public log(message: unknown, ...optionalParams: unknown[]): void {
        this.logger.log(message, ...optionalParams);
    }

    public debug(message: unknown, ...optionalParams: unknown[]): void {
        this.logger.debug(message, ...optionalParams);
    }

    public verbose(message: unknown, ...optionalParams: unknown[]): void {
        this.logger.verbose(message, ...optionalParams);
    }

    public fatal(message: unknown, ...optionalParams: unknown[]): void {
        this.logger.fatal(message, ...optionalParams);
    }

    public setLogLevels(levels: LogLevel[]): void {
        this.logger.setLogLevels(levels);
    }

    public forContext(context: string): Logger {
        return {
            info: (message, metadata) => {
                this.write("log", message, metadata, context);
            },
            warn: (message, metadata) => {
                this.write("warn", message, metadata, context);
            },
            error: (message, metadata) => {
                this.write("error", message, metadata, context);
            },
        };
    }

    private write(level: "error" | "log" | "warn", message: string, metadata?: Record<string, unknown>, context?: string): void {
        const payload = {
            message,
            ...(metadata ? {context: metadata} : {}),
        };

        if (level === "error") {
            this.logger.error(payload, context);
            return;
        }

        if (level === "warn") {
            this.logger.warn(payload, context);
            return;
        }

        this.logger.log(payload, context);
    }
}

export function createScopedLogger(logger: Logger, context: string): Logger {
    if (logger instanceof AppLogger) {
        return logger.forContext(context);
    }

    return {
        info(message, metadata) {
            logger.info(message, mergeContext(metadata, context));
        },
        warn(message, metadata) {
            logger.warn(message, mergeContext(metadata, context));
        },
        error(message, metadata) {
            logger.error(message, mergeContext(metadata, context));
        },
    };
}

function mergeContext(metadata: Record<string, unknown> | undefined, context: string): Record<string, unknown> {
    return {
        loggerContext: context,
        ...(metadata ?? {}),
    };
}
