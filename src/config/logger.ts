import type {Logger} from "../types/services.js";

function log(level: "INFO" | "WARN" | "ERROR", message: string, context?: Record<string, unknown>): void {
    const payload = {
        level,
        message,
        ...(context ? {context} : {}),
    };

    console.log(JSON.stringify(payload));
}

export const logger: Logger = {
    info(message, context) {
        log("INFO", message, context);
    },
    warn(message, context) {
        log("WARN", message, context);
    },
    error(message, context) {
        log("ERROR", message, context);
    },
};
