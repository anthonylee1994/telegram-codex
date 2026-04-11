import Database from "better-sqlite3";

import type {ChatSession} from "../types/conversation.js";
import type {ProcessedUpdateRepository, SessionRepository} from "../types/services.js";

interface SessionRow {
    chat_id: string;
    last_response_id: string | null;
    updated_at: number;
}

export class SqliteStorage implements SessionRepository, ProcessedUpdateRepository {
    private readonly db: Database.Database;

    public constructor(databasePath: string) {
        this.db = new Database(databasePath);
        this.db.pragma("journal_mode = WAL");
        this.migrate();
    }

    public async getByChatId(chatId: string): Promise<ChatSession | null> {
        const row = this.db.prepare("SELECT chat_id, last_response_id, updated_at FROM chat_sessions WHERE chat_id = ?").get(chatId) as SessionRow | undefined;

        if (!row) {
            return null;
        }

        return {
            chatId: row.chat_id,
            conversationState: row.last_response_id,
            updatedAt: row.updated_at,
        };
    }

    public async upsert(session: ChatSession): Promise<void> {
        this.db
            .prepare(
                `
          INSERT INTO chat_sessions (chat_id, last_response_id, updated_at)
          VALUES (@chatId, @conversationState, @updatedAt)
          ON CONFLICT(chat_id) DO UPDATE SET
            last_response_id = excluded.last_response_id,
            updated_at = excluded.updated_at
        `
            )
            .run(session);
    }

    public async delete(chatId: string): Promise<void> {
        this.db.prepare("DELETE FROM chat_sessions WHERE chat_id = ?").run(chatId);
    }

    public async hasProcessed(updateId: number): Promise<boolean> {
        const row = this.db.prepare("SELECT 1 FROM processed_updates WHERE update_id = ? LIMIT 1").get(updateId) as {1: number} | undefined;

        return Boolean(row);
    }

    public async markProcessed(updateId: number, chatId: string, messageId: number): Promise<void> {
        this.db
            .prepare(
                `
          INSERT OR IGNORE INTO processed_updates (update_id, chat_id, message_id, processed_at)
          VALUES (?, ?, ?, ?)
        `
            )
            .run(updateId, chatId, messageId, Date.now());
    }

    private migrate(): void {
        this.db.exec(`
      CREATE TABLE IF NOT EXISTS chat_sessions (
        chat_id TEXT PRIMARY KEY,
        last_response_id TEXT,
        updated_at INTEGER NOT NULL
      );

      CREATE TABLE IF NOT EXISTS processed_updates (
        update_id INTEGER PRIMARY KEY,
        chat_id TEXT NOT NULL,
        message_id INTEGER NOT NULL,
        processed_at INTEGER NOT NULL
      );
    `);
    }
}
