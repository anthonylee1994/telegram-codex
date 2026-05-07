# telegram-codex

一個用 NestJS 跑嘅 Telegram bot backend。Telegram 負責收用戶輸入，server 負責 webhook、session、長期記憶、rate limit，同埋 call `codex exec` 生成回覆。

Demo：https://t.me/On99AppBot

## 目的

呢個 project 係想將本機用緊嘅 Codex CLI 包成一個可以長期運行、真係日常用到嘅 Telegram bot，而唔係淨係做 demo。

- Telegram 做最順手嘅輸入入口
- server 控 webhook、session、memory、duplicate protection
- `codex exec` 做真正回覆生成

## 功能

- 支持 Telegram 文字訊息
- 支持單張圖片同 caption
- 支持 Telegram 相簿多圖訊息分析
- 支持 reply 舊文字或者舊圖片延續上下文
- 相簿冇 caption 時會自動補 prompt 做逐張描述同比較
- 相簿太多圖時會叫 user 縮窄範圍
- 支持 `/start`、`/help`、`/status`、`/session`、`/memory`、`/compact`、`/forget`、`/new`
- `/compact` 會非同步壓縮目前 session，再主動 send 摘要返 Telegram
- 支持最多 3 個 suggested replies
- 有 session memory 同獨立長期記憶
- 有 duplicate update 保護
- 有簡單 rate limit
- 可限制指定 Telegram user id

未支持：

- 語音
- 影片
- 一般非圖片檔案分析

## 技術棧

- TypeScript
- Node.js
- NestJS
- TypeORM
- SQLite
- Telegram Bot API
- Codex CLI

## 架構

而家個 codebase 主要跟 `conversation`、`codex`、`telegram` 分域。原則好簡單：

- 對外系統邊界，例如 Telegram API、Codex CLI，先值得保留 gateway/client abstraction
- 純本地 persistence 可以直接用 repository implementation，唔需要為咗「乾淨」再包一層假抽象
- 主流程 service 應該保持短同直，複雜分支就拆成有業務語意嘅 handler / helper
- `conversation` 管 session、memory、reply、processed update 呢啲 bot 內部業務
- `codex` 管 Codex CLI 整合（execution、parsing、reply、session、memory）
- `telegram` 管 Telegram API 整合（webhook、inbound、api、commands）
- `config` 管環境變數同配置，`database` 管 entities 同 migrations

## 主要 module map

### `conversation`

- `reply/reply-generation.service.ts`
  reply use case 主入口。
- `session/session.service.ts`
  session / memory / compact 管理。
- `reply/processed-update.service.ts`
  duplicate claim / replay / pending reply lifecycle。
- `storage/`
  repositories：chat-session、chat-memory、processed-update、media-group-buffer。
- `scheduler/job-scheduler.service.ts`
  reply generation job queue。

### `codex`

- `reply/codex-reply-client.service.ts`
  組 transcript / prompt，再 call `codex exec`。
- `execution/exec-runner.service.ts`
  process execution 包裝。
- `reply/prompt-builder.service.ts`
  組 system prompt 同 user prompt。
- `parsing/reply-parser.service.ts`
  parse `codex exec` output。
- `session/codex-session-compact-client.service.ts`
  call `codex exec` 做 session compaction。
- `memory/codex-memory-client.service.ts`
  call `codex exec` 做長期記憶 merge。

### `telegram`

- `webhook/telegram-webhook.controller.ts`
  `POST /telegram/webhook` 入口。
- `webhook/telegram-webhook.service.ts`
  inbound flow：parse 後 routing、command、guard、enqueue。
- `inbound/`
  parse Telegram update、route、handle commands、guard。
- `api/telegram-api.service.ts`
  Telegram API adapter（send message、download file、typing status）。
- `commands/`
  各種 command handlers（start、help、status、session、memory、compact、forget、new）。

### `config` / `database` / `health`

- `config/app-config.service.ts`
  環境變數 wrapper。
- `database/entities.ts`
  TypeORM entities。
- `health/health.controller.ts`
  `GET /health`。

## Runtime Flow

由 Telegram 打入嚟到 bot 回覆，大致係：

1. `POST /telegram/webhook` 打入 `TelegramWebhookController`
2. controller 驗 `X-Telegram-Bot-Api-Secret-Token`
3. `TelegramWebhookService` 接手處理 Telegram update
4. `TelegramUpdateParserService` 將 payload 轉成 `InboundMessage`
5. `InboundMessageRouterService` 判斷係直接處理，定係先 defer media group
6. `InboundMessageProcessorService` 依次交畀 unsupported / duplicate / command / guard steps 處理
7. 如果四關都過，`JobSchedulerService` 先 enqueue reply generation
8. `ReplyGenerationService` download 圖片、讀 session / memory，再經 `CodexReplyClientService` 走去 call `codex exec`
9. `CodexReplyClientService` 將 transcript + prompt 組好，經 `ExecRunnerService` call `codex exec`
10. reply send 成功後先更新 session、processed update 同長期記憶；`/compact` 就走另一條 async path，完成後由 `CompactResultSenderService` 主動 send 返 Telegram

## 超短 Codebase Map

如果只想最快明主 flow，可以直接睇呢幾個 file：

1. [telegram/webhook/telegram-webhook.controller.ts](./src/telegram/webhook/telegram-webhook.controller.ts)
   Telegram webhook HTTP 入口，只做驗 secret 同 handoff。

2. [telegram/webhook/telegram-webhook.service.ts](./src/telegram/webhook/telegram-webhook.service.ts)
   將 raw update parse 成 `InboundMessage`，再交俾 router。

3. [telegram/inbound/inbound-message-processor.service.ts](./src/telegram/inbound/inbound-message-processor.service.ts)
   入 reply flow 前嘅四關：unsupported、duplicate/replay、command、guard。

4. [conversation/reply/reply-generation.service.ts](./src/conversation/reply/reply-generation.service.ts)
   真正 reply use case 主入口：整 context、call model、send reply、persist session、refresh memory。

5. [codex/reply/codex-reply-client.service.ts](./src/codex/reply/codex-reply-client.service.ts)
   將 transcript 同 prompt 組好，再 call `codex exec`。

6. [telegram/api/telegram-api.service.ts](./src/telegram/api/telegram-api.service.ts)
   將 reply 轉成 Telegram HTML / keyboard markup，再 call Telegram API。

## 命名同架構規範

而家 repo 有幾條明確規矩：

- 真正跨外部邊界嘅 dependency 用 `*Gateway` interface + injection token（例如 `TelegramGateway` + `TELEGRAM_GATEWAY`）
- persistence / adapter implementation 用 `*Repository`、`*Service`、`*Client` 呢類有語意嘅名
- NestJS modules 用 `@Module()` 組織 providers 同 exports
- 主要 business logic 放 `@Injectable()` services
- HTTP controllers 只做 routing 同 validation，唔應該有 business logic

## 資料儲存

SQLite 主要有幾張表：

- `chat_sessions`
  每個 chat 一條 session context。

- `chat_memories`
  每個 chat 一條長期記憶，保留穩定偏好、背景、持續目標。

- `processed_updates`
  記 Telegram `update_id`，用嚟防 duplicate。

- `media_group_buffers`
  暫存 Telegram 相簿 buffer。

- `media_group_messages`
  暫存屬於某個相簿嘅多條訊息，flush 時再合併。

Lifecycle 大致係：

- `/new` 只清 `chat_sessions`
- `/forget` 只清 `chat_memories`
- `processed_updates` 會 opportunistic cleanup
- media group buffer flush 完就會刪

## 環境需求

- Node.js 18+
- pnpm 9+
- SQLite 3
- 本機或 server 可以直接跑 `codex exec`
- `~/.codex/config.toml` 同 `~/.codex/auth.json` 已配置好

## 環境變數

| 變數 | 用途 | 預設值 |
| --- | --- | --- |
| `PORT` | HTTP port | `3000` |
| `BASE_URL` | 對外 base URL，用嚟註冊 webhook | 無 |
| `TELEGRAM_BOT_TOKEN` | Telegram bot token | 無 |
| `TELEGRAM_WEBHOOK_SECRET` | Telegram webhook secret | 無 |
| `ALLOWED_TELEGRAM_USER_IDS` | 限定可用 Telegram user id，逗號分隔 | 空 |
| `SQLITE_DB_PATH` | SQLite database path | `./data/app.db` |
| `CODEX_EXEC_TIMEOUT_SECONDS` | `codex exec` timeout 秒數 | `300` |
| `MAX_MEDIA_GROUP_IMAGES` | Telegram 相簿最多接受幾多張圖 | `10` |
| `SESSION_TTL_DAYS` | session TTL 日數 | `7` |
| `MEDIA_GROUP_WAIT_MS` | 相簿聚合等待時間 | `1200` |
| `RATE_LIMIT_WINDOW_MS` | rate limit window | `10000` |
| `RATE_LIMIT_MAX_MESSAGES` | window 內最多訊息數 | `5` |

## 本地開發

1. 複製 `.env.example` 做 `.env`
2. 填好至少以下變數：
   - `BASE_URL`
   - `TELEGRAM_BOT_TOKEN`
   - `TELEGRAM_WEBHOOK_SECRET`
3. 安裝指定版本 Codex CLI

```bash
npm install -g @openai/codex@"$(cat .codex-version)"
```

4. 安裝 dependencies

```bash
pnpm install
```

5. 啟動 app

```bash
pnpm run dev
```

6. 註冊 webhook

```bash
pnpm run task telegram:set-webhook
```

7. 更新 Telegram command menu

```bash
pnpm run task telegram:update-commands
```

常用 endpoint：

- `GET /health`
- `POST /telegram/webhook`

## Dokku Deployment

repo 內有 [Dockerfile](./Dockerfile) 同 [Procfile](./Procfile)，所以可以直接用 Dokku deploy。不過有兩樣嘢一定要處理：

- SQLite database 要 persistent，否則重 deploy 或重開 container 之後 session / memory 會冇晒
- Codex CLI auth 要喺 runtime container 入面存在，否則 `codex exec` 根本跑唔起

### 1. 建 app

```bash
dokku apps:create telegram-codex
```

### 2. 設定 domain 同 TLS

```bash
dokku domains:set telegram-codex bot.example.com
dokku letsencrypt:enable telegram-codex
```

之後 `BASE_URL` 應該用 `https://bot.example.com`，唔好加 `/telegram/webhook`。

### 3. 準備 persistent directories

喺 server 開兩個目錄，一個放 SQLite data，一個放 Codex auth：

```bash
mkdir -p /var/lib/dokku/data/storage/telegram-codex/data
mkdir -p /var/lib/dokku/data/storage/telegram-codex/codex
```

mount 入 container：

```bash
dokku storage:mount telegram-codex /var/lib/dokku/data/storage/telegram-codex/data:/app/data
dokku storage:mount telegram-codex /var/lib/dokku/data/storage/telegram-codex/codex:/root/.codex
```

`/app/data` 係 SQLite 預設位置，`/root/.codex` 係 Codex CLI 會搵 `config.toml` 同 `auth.json` 嘅位。

### 4. 放入 Codex config / auth

將你可用嘅 Codex CLI 設定放入剛剛 mount 嗰個 host path：

```bash
cp ~/.codex/config.toml /var/lib/dokku/data/storage/telegram-codex/codex/config.toml
cp ~/.codex/auth.json /var/lib/dokku/data/storage/telegram-codex/codex/auth.json
```

如果你唔係喺同一部機 copy，就總之自己用安全方法將呢兩個 file 放到嗰個 directory。冇呢兩個 file，container 入面嘅 `codex exec` 用唔到。

### 5. 設 env

```bash
dokku config:set telegram-codex \
  BASE_URL=https://bot.example.com \
  TELEGRAM_BOT_TOKEN=replace-me \
  TELEGRAM_WEBHOOK_SECRET=replace-me \
  SQLITE_DB_PATH=/app/data/app.db
```

如有需要可以再加：

```bash
dokku config:set telegram-codex \
  ALLOWED_TELEGRAM_USER_IDS=123456789 \
  CODEX_EXEC_TIMEOUT_SECONDS=300 \
  MAX_MEDIA_GROUP_IMAGES=10 \
  SESSION_TTL_DAYS=7 \
  MEDIA_GROUP_WAIT_MS=1200 \
  RATE_LIMIT_WINDOW_MS=10000 \
  RATE_LIMIT_MAX_MESSAGES=5
```

### 6. Deploy

```bash
git push dokku main
```

如果你 remote 名唔係 `dokku`，自己改返。

### 7. 註冊 webhook

deploy 完之後，喺 server 跑：

```bash
dokku run telegram-codex node dist/task.js telegram:set-webhook
```

如要同步 Telegram command menu：

```bash
dokku run telegram-codex node dist/task.js telegram:update-commands
```

### 8. 檢查

```bash
curl -i https://bot.example.com/health
dokku logs telegram-codex -t
```

如果 webhook 唔通、`codex exec` fail，第一時間檢查：

- `/root/.codex/config.toml` 同 `/root/.codex/auth.json` 有冇 mount 到入 container
- `BASE_URL` 係咪真係對外可達 HTTPS URL
- `TELEGRAM_WEBHOOK_SECRET` 有冇同 Telegram webhook 設定一致
- `/app/data/app.db` 有冇寫入權限

## CLI Tasks

`task.ts` 而家支持：

- `telegram:set-webhook`
- `telegram:update-commands`

用法：

```bash
pnpm run task <task-name>
```

或者喺 production：

```bash
node dist/task.js <task-name>
```

## Telegram Commands

- `/start`
  顯示 welcome / help message，同時清 reply keyboard。

- `/help`
  列出可用 command 同支持輸入類型。

- `/status`
  睇 bot runtime 狀態。

- `/session`
  睇目前 chat session 狀態。

- `/memory`
  睇目前 chat 長期記憶。

- `/forget`
  清除目前 chat 長期記憶。

- `/compact`
  非同步壓縮目前對話 context。

- `/new`
  清除目前 chat session，重新開始。

`/new` 唔會刪長期記憶；如果連長期記憶都想清，就用 `/forget`。

## 測試

跑全部測試：

```bash
pnpm run test
```

Type check：

```bash
pnpm run typecheck
```

Prettier check：

```bash
pnpm run prettier:check
```

Build production：

```bash
pnpm run build
```

## Debug

### Health check

```bash
curl -i http://localhost:3000/health
```

### 開 SQLite 睇資料

```bash
sqlite3 ./data/app.db
```

常用查詢：

```sql
SELECT * FROM chat_sessions WHERE chat_id = '123456';
SELECT * FROM chat_memories WHERE chat_id = '123456';
SELECT * FROM processed_updates WHERE update_id = 123456789;
SELECT * FROM processed_updates ORDER BY update_id DESC LIMIT 20;
SELECT * FROM media_group_buffers;
SELECT * FROM media_group_messages WHERE media_group_key = '123456:album-1';
```

### 手動重設

- 想清 session：`/new`
- 想清長期記憶：`/forget`
- 想壓縮現有 context：`/compact`
