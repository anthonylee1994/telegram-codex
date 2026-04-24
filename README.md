# telegram-codex

一個用 Spring Boot 跑嘅 Telegram bot backend。Telegram 負責收用戶輸入，server 負責 webhook、session、長期記憶、rate limit，同埋 call `codex exec` 生成回覆。

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

- Java 25
- Spring Boot 3.5
- Spring Web
- Spring Data JPA
- Flyway
- SQLite
- Telegram Bot API
- Codex CLI

## 架構

而家個 codebase 主要跟 `conversation`、`integration`、`interfaces` 分域，但唔再盲目追求 layer purity。原則係：

- 對外系統邊界，例如 Telegram API、Codex CLI，先值得保留 `Gateway`
- 呢類真邊界而家統一放喺 `conversation/application/gateway`（只保留必要嘅）
- 純本地 persistence 可以直接用 repository implementation，唔需要為咗「乾淨」再包一層假抽象
- 主流程 class 應該保持短同直，複雜分支就拆成有業務語意嘅 handler / helper
- 單一 file 嘅 package 會直接放喺 parent directory，避免過深 nesting

```text
src/main/java/com/telegram/codex/
├── bootstrap/
│   └── TelegramCodexApplication.java
├── conversation/
│   ├── application/
│   │   ├── JobSchedulerService.java
│   │   ├── ProcessedUpdateService.java
│   │   ├── gateway/
│   │   │   └── ReplyGenerationGateway.java
│   │   ├── reply/
│   │   │   ├── ReplyGenerationService.java
│   │   │   ├── ReplyResult.java
│   │   │   └── AttachmentDownloader.java
│   │   └── session/
│   │       ├── SessionService.java
│   │       └── CodexSessionCompactClient.java
│   ├── domain/
│   │   ├── memory/
│   │   ├── session/
│   │   └── update/
│   └── infrastructure/
│       ├── memory/
│       │   ├── ChatMemoryRepository.java
│       │   └── CodexMemoryClient.java
│       ├── persistence/
│       ├── session/
│       │   ├── ChatSessionRepository.java
│       │   └── CodexSessionCompactClient.java
│       └── update/
├── integration/
│   ├── codex/
│   └── telegram/
│       ├── application/
│       ├── domain/
│       └── infrastructure/
├── interfaces/
│   ├── cli/
│   └── web/
└── shared/
    └── config/
```

### 分層原則

- `domain` 放純業務概念、規則、value-like model
- `application` 放 use case orchestration，同少量貼業務嘅 helper
- `infrastructure` 放 JPA、repository、adapter implementation
- `integration` 放對外系統整合，例如 Telegram 同 Codex
- `interfaces` 放 HTTP / CLI entrypoints
- `shared` 只留真係跨 domain 都合理共享嘅 config

## 主要 package map

### `bootstrap`

- `bootstrap/TelegramCodexApplication.java`
  Spring Boot 入口。

### `conversation`

- `conversation/application/reply/ReplyGenerationService.java`
  對話回覆 use case 主入口，負責附件 download、reply generation、session persist、memory refresh。

- `conversation/application/session/SessionService.java`
  session 讀寫、TTL、compact、同埋長期記憶管理。

- `conversation/application/JobSchedulerService.java`
  跑 async reply、compact，同 media group flush scheduling。

- `conversation/application/ProcessedUpdateService.java`
  duplicate update claim / replay / prune / pending reply 管理。

- `conversation/domain/*`
  對話核心規則，例如 `ChatRateLimiter`、`MediaGroupMerger`、`Transcript`、memory/session/update records。

- `conversation/infrastructure/*`
  conversation domain 對應嘅 repository / client。session / memory 呢類純本地 persistence 會畀 application 直接用。

### `integration/codex`

- `CodexReplyClient`
  真正接 `codex exec` 生成 reply。

- `ExecRunner`
  process execution 包裝。

- `PromptBuilder`
  組 prompt。

- `ReplyParser`
  parse Codex output。

### `integration/telegram`

- `application/webhook/TelegramWebhookHandler`
  Telegram inbound flow 入口。

- `application/webhook/InboundMessageProcessor`
  webhook 主流程 orchestration，將 unsupported、duplicate/replay、command、guard、enqueue 順序串起。

- `application/webhook/DuplicateUpdateHandler`
  duplicate / replay 判斷同 resend。

- `application/webhook/ReplyRequestGuard`
  unauthorized、too many images、sensitive intent、rate limit、claim processing 呢堆前置檢查。

- `application/webhook/TelegramCommandHandler`
  `/start`、`/new`、`/compact` 等 command 分支。

- `application/webhook/TelegramCommandRegistry`
  Telegram command 判斷。

- `application/CompactResultSender`
  `/compact` 完成後主動 send 結果。

- `infrastructure/TelegramClient`
  Telegram API adapter。

- `infrastructure/TelegramUpdateParser`
  將 webhook payload 轉成 app 內部 `InboundMessage`。

- `infrastructure/AttachmentDownloader`
  附件下載 adapter。

### `interfaces`

- `interfaces/web/TelegramWebhookController.java`
  `POST /telegram/webhook` 入口。

- `interfaces/web/HealthController.java`
  `GET /health`。

- `interfaces/cli/CliTaskRunner.java`
  CLI task 入口，例如 set webhook、update commands。

## Runtime Flow

由 Telegram 打入嚟到 bot 回覆，大致係：

1. `POST /telegram/webhook` 打入 `TelegramWebhookController`
2. controller 驗 `X-Telegram-Bot-Api-Secret-Token`
3. `TelegramWebhookHandler` 接手處理 Telegram update
4. `TelegramUpdateParser` 將 payload 轉成 `InboundMessage`
5. `TelegramWebhookRouter` 判斷係直接處理，定係先 defer media group
6. `InboundMessageProcessor` 依次交畀 unsupported / duplicate / command / guard steps 處理
7. 如果四關都過，`JobSchedulerService` 先 enqueue reply generation
8. `ReplyGenerationService` download 圖片、讀 session / memory，再經 `ReplyGenerationGateway` 走去 `CodexReplyClient`
9. `CodexReplyClient` 將 transcript + prompt 組好，經 `ExecRunner` call `codex exec`
10. reply send 成功後先更新 session、processed update 同長期記憶；`/compact` 就走另一條 async path，完成後由 `CompactResultSender` 主動 send 返 Telegram

## 超短 Codebase Map

如果只想最快明主 flow，可以直接睇呢幾個 class：

1. [`interfaces/web/TelegramWebhookController.java`](./src/main/java/com/telegram/codex/interfaces/web/TelegramWebhookController.java)
   Telegram webhook HTTP 入口，只做驗 secret 同 handoff。

2. [`integration/telegram/application/webhook/TelegramWebhookHandler.java`](./src/main/java/com/telegram/codex/integration/telegram/application/webhook/TelegramWebhookHandler.java)
   將 raw update parse 成 `InboundMessage`，再交俾 router。

3. [`integration/telegram/application/webhook/InboundMessageProcessor.java`](./src/main/java/com/telegram/codex/integration/telegram/application/webhook/InboundMessageProcessor.java)
   入 reply flow 前嘅四關：unsupported、duplicate/replay、command、guard。

4. [`conversation/application/reply/ReplyGenerationService.java`](./src/main/java/com/telegram/codex/conversation/application/reply/ReplyGenerationService.java)
   真正 reply use case 主入口：整 context、call model、send reply、persist session、refresh memory。

5. [`integration/codex/CodexReplyClient.java`](./src/main/java/com/telegram/codex/integration/codex/CodexReplyClient.java)
   將 transcript 同 prompt 組好，再 call `codex exec`。

6. [`integration/telegram/infrastructure/TelegramClient.java`](./src/main/java/com/telegram/codex/integration/telegram/infrastructure/TelegramClient.java)
   將 reply 轉成 Telegram HTML / keyboard markup，再 call Telegram API。

## 命名同架構規範

而家 repo 有幾條明確規矩，唔好再加舊 style 名：

- 真正跨外部邊界嘅 dependency 先用 `*Gateway`（例如 `ReplyGenerationGateway`、`TelegramGateway`）
- persistence / adapter implementation 用 `*Repository`、`*Client` 呢類有語意嘅名
- 禁止再新增 `*Store` 命名
- `conversation/application` 可以直接依賴 `conversation/infrastructure`
- `interfaces` layer 唔可以直接 import `infrastructure` implementation
- `integration/telegram/application` 唔可以直接依賴 `integration/telegram/infrastructure`

對應檢查喺 `src/test/java/com/telegram/codex/architecture/ArchitectureTest.java`。因為 ArchUnit 喺 Java 25 有 class file version 問題，所以呢度用 source-based architecture test 直接掃 import 同 type declaration。

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

- Java 25
- Gradle 9.x
- SQLite 3
- Node.js / npm
- 本機或 server 可以直接跑 `codex exec`
- `~/.codex/config.toml` 同 `~/.codex/auth.json` 已配置好

repo 已包 `./gradlew`，唔使自己另外裝 Gradle。

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

4. 啟動 app

```bash
./gradlew bootRun
```

5. 註冊 webhook

```bash
bin/telegram-codex telegram:set-webhook
```

6. 更新 Telegram command menu

```bash
bin/telegram-codex telegram:update-commands
```

常用 endpoint：

- `GET /health`
- `POST /telegram/webhook`

## Dokku Deployment

repo 內有 [Dockerfile](/Users/anthony/Documents/Development/telegram-codex/Dockerfile) 同 [Procfile](/Users/anthony/Documents/Development/telegram-codex/Procfile)，所以可以直接用 Dokku deploy。不過有兩樣嘢一定要處理：

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
dokku run telegram-codex /app/bin/telegram-codex telegram:set-webhook
```

如要同步 Telegram command menu：

```bash
dokku run telegram-codex /app/bin/telegram-codex telegram:update-commands
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

`CliTaskRunner` 而家支持：

- `telegram:set-webhook`
- `telegram:update-commands`

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
./gradlew test
```

如果你改咗架構分層、命名或者 package dependency，記得至少睇埋：

- `src/test/java/com/telegram/codex/architecture/ArchitectureTest.java`

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
