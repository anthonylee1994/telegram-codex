import {Inject, Injectable} from "@nestjs/common";
import Database from "better-sqlite3";
import {APP_ENV} from "../config/tokens.js";
import type {AppEnv} from "../config/env.js";
import type {ProcessedUpdateRepository, SessionRepository} from "../config/service.types.js";
import type {ChatSession, ProcessedUpdate} from "../conversation/conversation.types.js";

interface SessionRow {
    chat_id: string;
    last_response_id: string | null;
    updated_at: number;
}

interface ProcessedUpdateRow {
    chat_id: string;
    conversation_state: string | null;
    message_id: number;
    reply_text: string | null;
    sent_at: number | null;
    update_id: number;
}

@Injectable()
export class SqliteStorage implements SessionRepository, ProcessedUpdateRepository {
    private readonly db: Database.Database;

    public constructor(@Inject(APP_ENV) env: AppEnv) {
        this.db = new Database(env.SQLITE_DB_PATH);
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

    public async getByUpdateId(updateId: number): Promise<ProcessedUpdate | null> {
        const row = this.db.prepare("SELECT update_id, chat_id, message_id, reply_text, conversation_state, sent_at FROM processed_updates WHERE update_id = ? LIMIT 1").get(updateId) as
            | ProcessedUpdateRow
            | undefined;

        if (!row) {
            return null;
        }

        return {
            chatId: row.chat_id,
            conversationState: row.conversation_state,
            messageId: row.message_id,
            replyText: row.reply_text,
            sentAt: row.sent_at,
            updateId: row.update_id,
        };
    }

    public async savePendingReply(updateId: number, chatId: string, messageId: number, replyText: string, conversationState: string): Promise<void> {
        this.db
            .prepare(
                `
          INSERT INTO processed_updates (update_id, chat_id, message_id, processed_at, reply_text, conversation_state, sent_at)
          VALUES (?, ?, ?, ?, ?, ?, NULL)
          ON CONFLICT(update_id) DO UPDATE SET
            chat_id = excluded.chat_id,
            message_id = excluded.message_id,
            processed_at = excluded.processed_at,
            reply_text = excluded.reply_text,
            conversation_state = excluded.conversation_state
        `
            )
            .run(updateId, chatId, messageId, Date.now(), replyText, conversationState);
    }

    public async markProcessed(updateId: number, chatId: string, messageId: number): Promise<void> {
        this.db
            .prepare(
                `
          INSERT INTO processed_updates (update_id, chat_id, message_id, processed_at, sent_at)
          VALUES (?, ?, ?, ?, ?)
          ON CONFLICT(update_id) DO UPDATE SET
            chat_id = excluded.chat_id,
            message_id = excluded.message_id,
            processed_at = excluded.processed_at,
            sent_at = excluded.sent_at
        `
            )
            .run(updateId, chatId, messageId, Date.now(), Date.now());
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
        processed_at INTEGER NOT NULL,
        reply_text TEXT,
        conversation_state TEXT,
        sent_at INTEGER
      );
    `);

        const columns = this.db.prepare("PRAGMA table_info(processed_updates)").all() as Array<{name: string}>;
        const columnNames = new Set(
            columns.map(function mapColumn(column: {name: string}): string {
                return column.name;
            })
        );

        if (!columnNames.has("reply_text")) {
            this.db.exec("ALTER TABLE processed_updates ADD COLUMN reply_text TEXT");
        }

        if (!columnNames.has("conversation_state")) {
            this.db.exec("ALTER TABLE processed_updates ADD COLUMN conversation_state TEXT");
        }

        if (!columnNames.has("sent_at")) {
            this.db.exec("ALTER TABLE processed_updates ADD COLUMN sent_at INTEGER");
        }
    }
}
