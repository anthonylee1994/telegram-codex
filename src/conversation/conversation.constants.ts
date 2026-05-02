export const CONVERSATION_CONSTANTS = {
    minTranscriptSizeForCompact: 4,
    processedUpdatePruneIntervalMs: 6 * 60 * 60 * 1000,
    processedUpdateRetentionMs: 30 * 24 * 60 * 60 * 1000,
};

export function formatConversationTime(epochMillis: number): string {
    return new Intl.DateTimeFormat("sv-SE", {
        timeZone: "Asia/Hong_Kong",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false,
        timeZoneName: "short",
    }).format(new Date(epochMillis));
}
