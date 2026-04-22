# telegram-codex

用 Telegram webhook 收訊息，之後 enqueue background work 跑 `codex exec` 做回覆；對話狀態用 SQLite 存，而家個 HTTP layer 係 Spring Boot API。

Demo：https://t.me/On99AppBot

## 點解會整呢個 project

呢個 project 其實係由一個幾實際嘅需求出發：

- 想隨時隨地用 Codex 幫手
- 唔想另外再砌一套 OpenAI API key integration
- 想保留基本對話記錄，而唔係每次都由零開始

所以最後就變成一個 Telegram bot backend：

- Telegram 負責做最順手嘅輸入入口
- server 負責收 webhook、做 session / 長期記憶、控 rate limit
- `codex exec` 負責真正生成回覆

簡單講，呢個 project 係想將本機用緊嘅 Codex CLI，包成一個可以長期運行、日常真係用得着嘅 bot，而唔係淨係做 demo。

## 功能

- 支援 Telegram 文字訊息
- 支援單張圖片同 caption
- 支援 Telegram 相簿多圖訊息分析
- 支援 Telegram PDF document，會先轉頭幾頁做圖片再分析
- 支援 `.txt`、`.md`、`.html`、`.json`、`.csv`、`.docx`、`.xlsx` document，會先抽文字再分析
- 支援 reply 之前嘅 message；如果引用舊文字，會就住嗰句延續回答
- 支援 reply 之前嘅相 / PDF / 文字檔；就算今次冇重新 upload，都會拎返被引用文件再分析
- 多圖分析會用 `圖 1`、`圖 2` 呢類編號逐張講
- 相簿冇 caption 時會自動補 prompt，叫模型逐張描述再比較
- 相簿太多圖時會先叫用戶縮窄範圍再分析
- 支援 `/start` 顯示 welcome / help message
- 支援 `/help`、`/status`、`/session`、`/memory`、`/summary`
- 支援 `/forget` 清除長期記憶
- 支援 `/new` 重開當前 chat session
- `/summary` 會非同步將長對話壓縮成新 context，再主動 send 摘要返 Telegram
- 支援最多 3 個 reply keyboard suggested replies
- 有 session memory
- 有獨立長期記憶，會自動整理用戶偏好、背景、持續目標，再喺之後對話 relevant 時帶返入 prompt
- 有 duplicate update 保護
- 有簡單 rate limit
- 可限制指定 Telegram user id

未支援：

- 語音、影片、其他檔案

## Spring Boot 架構

核心 runtime 係 Spring Boot 3.5 + JPA + Flyway + SQLite。

```text
src/main/java/com/telegramcodex/
├── TelegramCodexApplication.java
├── cli/
│   └── CliTaskRunner.java
├── codex/
│   ├── CliClient.java
│   ├── ExecRunner.java
│   ├── PromptBuilder.java
│   ├── ReplyParser.java
│   └── Transcript.java
├── config/
│   ├── AppProperties.java
│   └── JacksonConfig.java
├── conversation/
│   ├── ConversationService.java
│   ├── MediaGroupStore.java
│   ├── MemoryClient.java
│   ├── ProcessedUpdateFlow.java
│   ├── ReplyGenerationFlow.java
│   ├── SessionSummaryClient.java
│   └── webhooks/
│       ├── ActionExecutor.java
│       ├── Decision.java
│       └── DecisionResolver.java
├── documents/
│   ├── PdfPageRasterizer.java
│   └── TextDocumentExtractor.java
├── jobs/
│   └── JobSchedulerService.java
├── persistence/
│   ├── ChatMemoryEntity.java
│   ├── ChatSessionEntity.java
│   ├── MediaGroupBufferEntity.java
│   ├── MediaGroupMessageEntity.java
│   └── ProcessedUpdateEntity.java
├── telegram/
│   ├── InboundMessage.java
│   ├── InboundMessageProcessor.java
│   ├── SummaryResultSender.java
│   ├── TelegramClient.java
│   ├── TelegramUpdateParser.java
│   └── TelegramWebhookHandler.java
└── web/
    ├── HealthController.java
    └── TelegramWebhookController.java
src/main/resources/
├── application.yml
└── db/migration/
    ├── V1__create_app_tables.sql
    └── V2__fix_updated_at_bigint_columns.sql
src/test/java/com/telegramcodex/
└── ...
```

個分層思路其實冇變，只係由 Rails service-oriented 寫法搬去 Java：

- controller 只處理 HTTP request / response
- parser 專心將 Telegram payload 轉成 app message
- conversation / telegram / codex service 負責業務邏輯
- persistence layer 只處理資料存取

咁做嘅目的都一樣：

- webhook flow 易測
- `codex exec` integration 同 HTTP 層分開
- SQLite / Telegram / Codex 三個依賴點唔會黐埋一舊

## Request Flow

由 Telegram 打入嚟，到 bot 回覆，而家條 path 係：

- `POST /telegram/webhook` 驗證 `X-Telegram-Bot-Api-Secret-Token`
- `TelegramUpdateParser` 將 Telegram payload 轉成 `InboundMessage`
- `InboundMessageProcessor` 做 duplicate、防重送、allowed user、commands、rate limit
- `MediaGroupStore` 將 Telegram 相簿訊息寫入 SQLite shared store，再由 `JobSchedulerService` 排 flush
- `ConversationService` 管 session TTL、長期記憶、summary，同 `CliClient` 串 `codex exec`
- `ReplyGenerationFlow` 非同步做附件 download、PDF 轉圖、文字檔抽字、主回答生成
- `SummaryResultSender` 將 `/summary` 結果主動 send 返 Telegram
- `ChatSession` / `ChatMemory` / `ProcessedUpdate` / `MediaGroupBuffer` / `MediaGroupMessage` 都係用 SQLite 存
- `bin/telegram-codex telegram:set-webhook` 同 `telegram:update-commands` 取代舊 task

拆開講：

1. Telegram webhook 打入 Spring Boot。
2. [`TelegramWebhookController`](src/main/java/com/telegramcodex/web/TelegramWebhookController.java) 先驗 secret token。
3. 驗證通過後，controller 將 payload 交畀 [`TelegramWebhookHandler`](src/main/java/com/telegramcodex/telegram/TelegramWebhookHandler.java)。
4. handler 先用 [`TelegramUpdateParser`](src/main/java/com/telegramcodex/telegram/TelegramUpdateParser.java) parse 文字 / 圖片 / PDF / 文字檔 / reply context。
5. 如果係 Telegram 相簿，會先寫入 [`MediaGroupStore`](src/main/java/com/telegramcodex/conversation/MediaGroupStore.java)，再排 delayed flush。
6. 非相簿 message 就交畀 [`InboundMessageProcessor`](src/main/java/com/telegramcodex/telegram/InboundMessageProcessor.java) 做 decision。
7. processor 會處理 duplicate、pending reply replay、allowed users、`/start`、`/help`、`/status`、`/session`、`/memory`、`/forget`、`/summary`、`/new` 同 chat-level rate limit。
8. 真正要生成回覆時，[`JobSchedulerService`](src/main/java/com/telegramcodex/jobs/JobSchedulerService.java) 會用 virtual thread enqueue reply generation，webhook thread 就即刻回 `200 OK`。
9. [`ReplyGenerationFlow`](src/main/java/com/telegramcodex/conversation/ReplyGenerationFlow.java) 會 download 附件、必要時將 PDF 轉 PNG、將文字檔抽成 prompt text，再 call [`ConversationService`](src/main/java/com/telegramcodex/conversation/ConversationService.java)。
10. [`CliClient`](src/main/java/com/telegramcodex/codex/CliClient.java) 用 transcript + system prompt 跑 `codex exec`，再生成最多 3 個 suggested replies。
11. reply 成功 send 番 Telegram 之後，system 先更新 session state 同長期記憶。
12. `/summary` 會走另一條 async path，整理完再主動 send 摘要返 Telegram。

## 主要檔案點運作

下面呢段係比你開 repo 想快速認路用。

### HTTP 層

- [`TelegramCodexApplication.java`](src/main/java/com/telegramcodex/TelegramCodexApplication.java)
  - Spring Boot 入口。
  - 起 app 前會先根據 `SQLITE_DB_PATH` 建好 database directory。

- [`HealthController.java`](src/main/java/com/telegramcodex/web/HealthController.java)
  - 提供 `GET /health`。
  - 比 load balancer、Dokku、自己 curl check service 仲生勾勾。

- [`TelegramWebhookController.java`](src/main/java/com/telegramcodex/web/TelegramWebhookController.java)
  - Telegram webhook 真入口。
  - 只做三件事：驗 secret、call handler、將結果轉成 HTTP status。
  - 回覆生成已經唔喺 request thread 做，webhook 主要負責快速 ack Telegram。

### Telegram / Webhook

- [`TelegramWebhookHandler.java`](src/main/java/com/telegramcodex/telegram/TelegramWebhookHandler.java)
  - webhook flow 入口。
  - 負責 parse update，同埋將 Telegram 相簿 defer 去 flush path。

- [`TelegramUpdateParser.java`](src/main/java/com/telegramcodex/telegram/TelegramUpdateParser.java)
  - 將 Telegram 原始 payload 轉成 app 內部用嘅 `InboundMessage`。
  - 支援文字訊息、單張圖片、圖片 document、PDF、文字 document 同 Telegram 相簿訊息。
  - user 如果 reply 之前一則 message，呢層會一齊抽返被引用文字、相、PDF、文字檔 context。

- [`InboundMessageProcessor.java`](src/main/java/com/telegramcodex/telegram/InboundMessageProcessor.java)
  - 大部分 Telegram 行為都喺呢度做 decision / action routing。
  - 包括 unsupported fallback、duplicate ignore、pending reply replay、unauthorized user reject、commands、rate limit、media group aggregation 後續處理。

- [`TelegramClient.java`](src/main/java/com/telegramcodex/telegram/TelegramClient.java)
  - 包住 Telegram Bot API。
  - 主要做 send message、send / remove reply keyboard、send typing action、download file、set webhook、update commands。

### Conversation / Codex

- [`ConversationService.java`](src/main/java/com/telegramcodex/conversation/ConversationService.java)
  - 對話層 orchestration。
  - 主要責任：
    - 讀寫 session / memory / processed update
    - 判斷 session TTL
    - call `CliClient`
    - call `MemoryClient` 更新長期記憶
    - call `SessionSummaryClient` 壓縮 session context
    - opportunistic prune 舊 processed updates

- [`ReplyGenerationFlow.java`](src/main/java/com/telegramcodex/conversation/ReplyGenerationFlow.java)
  - 真正處理 Telegram 附件 download 嗰層。
  - 收到 PDF 會先 download，再用 [`PdfPageRasterizer`](src/main/java/com/telegramcodex/documents/PdfPageRasterizer.java) 將頭幾頁轉做 PNG。
  - 收到 `.txt`、`.md`、`.html`、`.json`、`.csv`、`.docx`、`.xlsx` 會先用 [`TextDocumentExtractor`](src/main/java/com/telegramcodex/documents/TextDocumentExtractor.java) 抽文字再拼入 prompt。
  - 如果今次冇新附件，但 reply 咗之前一份相 / PDF / 文字檔，亦會 fallback download 嗰份被引用文件再分析。

- [`CliClient.java`](src/main/java/com/telegramcodex/codex/CliClient.java)
  - 真正同 `codex exec` 接軌嗰層。
  - 會 parse 上次 conversation state、relevant 時帶長期記憶入 prompt、處理 reply 舊訊息 context、多圖分析 prompt、suggested replies，同讀返 `codex exec --output-last-message`。

- [`MemoryClient.java`](src/main/java/com/telegramcodex/conversation/MemoryClient.java)
  - 專責將最新 user message 同 assistant reply merge 入長期記憶。
  - 只保留穩定偏好、背景、持續目標，唔會將短期 task context 原封不動抄落去。

- [`SessionSummaryClient.java`](src/main/java/com/telegramcodex/conversation/SessionSummaryClient.java)
  - `/summary` 用嘅 session 壓縮器。
  - 整理長對話，保留重點，之後主動 send 番摘要。

### Jobs / Scheduling

- [`JobSchedulerService.java`](src/main/java/com/telegramcodex/jobs/JobSchedulerService.java)
  - 用 virtual threads 跑 reply generation 同 summary。
  - 另外用 single-thread scheduler 做 Telegram 相簿 flush deadline。
  - 唔需要額外 queue worker process。

- [`MediaGroupStore.java`](src/main/java/com/telegramcodex/conversation/MediaGroupStore.java)
  - 包住 `media_group_buffers` 同 `media_group_messages`。
  - 負責 enqueue album update、判斷 flush deadline、同到鐘之後 aggregate 成一條 `InboundMessage`。

- [`SummaryResultSender.java`](src/main/java/com/telegramcodex/telegram/SummaryResultSender.java)
  - `/summary` 整完之後主動 send 結果返 Telegram。

### Persistence / Config

- [`AppProperties.java`](src/main/java/com/telegramcodex/config/AppProperties.java)
  - 將 ENV parse 成 app 用嘅 config object。
  - 包含 validation，例如 `baseUrl`、bot token、webhook secret 唔可以留空。

- [`application.yml`](src/main/resources/application.yml)
  - Spring Boot 主設定。
  - 會 import `.env`、設 datasource、JPA、Flyway、virtual threads 同 app 自訂 config。

- [`V1__create_app_tables.sql`](src/main/resources/db/migration/V1__create_app_tables.sql)
  - 建立 app runtime 需要嘅 SQLite tables。

- [`CliTaskRunner.java`](src/main/java/com/telegramcodex/cli/CliTaskRunner.java)
  - 支援 CLI task：
    - `telegram:set-webhook`
    - `telegram:update-commands`

## 資料點存

SQLite 而家主要有五張表：

- `chat_sessions`
  - 每個 chat 一條 session state
  - 用嚟保留對話上下文

- `chat_memories`
  - 每個 chat 一條長期記憶
  - 用嚟保留穩定偏好、背景、持續目標等可跨 session 重用嘅摘要

- `processed_updates`
  - 每個 Telegram `update_id` 一條處理記錄
  - 用嚟防 duplicate，同埋保留 pending reply replay 所需資料

- `media_group_buffers`
  - 每個 pending Telegram album 一條 buffer
  - 用嚟記最新 flush deadline

- `media_group_messages`
  - 每個屬於 album 嘅 Telegram update 一條記錄
  - 用嚟喺 flush 時 aggregate 返成一條 multi-image message

實際 lifecycle 係：

- `chat_sessions`
  - `/new` 會清
  - 過咗 `SESSION_TTL_DAYS` 會喺下次用到嗰個 chat 時 lazy cleanup

- `chat_memories`
  - `/forget` 會清
  - `/new` 唔會清，因為長期記憶係獨立設計
  - 只會喺 reply 成功送出後先更新，避免 send fail 時寫咗半套狀態

- `processed_updates`
  - reply send 成功後會保留
  - `ConversationService` 會 opportunistic 清走已送出而且夠舊嘅 records

- `media_group_buffers` / `media_group_messages`
  - album update 入 webhook 時建立 / 更新
  - flush 成功後即刻刪走
  - 主要作用係做短暫 debounce buffer，唔係長期資料

## 需求

- Java `25`
- Gradle `9.x`（repo 已包 `./gradlew`）
- SQLite 3
- `pdftoppm`（如果要用 PDF 轉圖分析；Docker image 已安裝 `poppler-utils`）
- `unzip`（如果要抽 `.docx` / `.xlsx` 內容；Docker image 已安裝）
- Node.js / npm（如果你想喺本機裝 `.codex-version` 指定嘅 Codex CLI）
- 本機或 server 可以直接跑 `codex exec`
- `~/.codex/config.toml` 同 `~/.codex/auth.json` 已配置好
- 本機最好用 repo 根目錄 `.codex-version` 指定嗰個 Codex CLI 版本

## 環境變數

| 變數 | 用途 | 預設值 |
| --- | --- | --- |
| `PORT` | HTTP port | `3000` |
| `BASE_URL` | 對外 base URL，用嚟註冊 webhook | 無 |
| `TELEGRAM_BOT_TOKEN` | Telegram bot token | 無 |
| `TELEGRAM_WEBHOOK_SECRET` | Telegram webhook secret header | 無 |
| `ALLOWED_TELEGRAM_USER_IDS` | 限定可用 Telegram user id，逗號分隔 | 空 |
| `SQLITE_DB_PATH` | SQLite database path | `./data/app.db` |
| `CODEX_EXEC_TIMEOUT_SECONDS` | `codex exec` 最多跑幾多秒先當 timeout | `300` |
| `MAX_MEDIA_GROUP_IMAGES` | 相簿最多接受幾多張圖先叫 user 縮窄範圍 | `6` |
| `MAX_PDF_PAGES` | 每份 PDF 最多轉幾頁做圖片分析 | `4` |
| `SESSION_TTL_DAYS` | session 過期日數 | `7` |
| `MEDIA_GROUP_WAIT_MS` | Telegram 相簿多圖聚合等待時間 | `1200` |
| `RATE_LIMIT_WINDOW_MS` | rate limit window | `10000` |
| `RATE_LIMIT_MAX_MESSAGES` | window 內最多幾多訊息 | `5` |

## 本地開發

1. 複製 `.env.example` 做 `.env`
2. 填好環境變數：
   - `TELEGRAM_BOT_TOKEN`
   - `BASE_URL`（例如 `https://your-domain.com`）
   - `TELEGRAM_WEBHOOK_SECRET`
   - `ALLOWED_TELEGRAM_USER_IDS`（可選）
3. 確保本機裝好 Codex CLI，或者用 `.codex-version` 指定版本：

```bash
npm install -g @openai/codex@"$(cat .codex-version)"
```

4. build app：

```bash
./gradlew bootJar
```

5. 啟動 server：

```bash
./gradlew bootRun
```

或者：

```bash
bin/telegram-codex
```

6. 註冊 webhook：

```bash
bin/telegram-codex telegram:set-webhook
```

7. 如要同步 Telegram bot command menu：

```bash
bin/telegram-codex telegram:update-commands
```

主要 endpoint：

- `GET /health`
- `POST /telegram/webhook`

## Telegram commands

- `/start`：顯示 welcome / help message，同時清走而家個 reply keyboard
- `/help`：列出可用 command 同支援輸入類型
- `/status`：睇 bot runtime 狀態，例如 session / memory / config 概況
- `/session`：睇目前 chat session 狀態
- `/memory`：睇目前 chat 嘅長期記憶內容
- `/forget`：清除目前 chat 嘅長期記憶
- `/summary`：非同步整理目前對話，壓縮成新 context，整完會再主動 send 摘要
- `/new`：清除當前 chat 嘅 session memory，下一句重新開始，同時清走而家個 reply keyboard

`/new` 只會重開短期 session，唔會刪長期記憶；如果你連長期記憶都想清走，要另外打 `/forget`。

平時直接 send 文字或者圖片畀 bot 就得，唔需要 command。撳 suggested reply 會由 Telegram client 直接送出一條新 message，所以 chat 入面會見到自己嘅綠色訊息。

如果你用 Telegram 個 reply 功能：

- reply 舊文字：bot 會當你係就住嗰句延續問落去
- reply 舊相：bot 會重新下載返張被引用嘅相再分析
- reply 舊 PDF：bot 會重新攞返份 PDF，轉頭幾頁做圖再分析
- reply 舊文字檔：bot 會重新抽返份檔案內容，再按你今次問題回答

而家個優先次序係：

- 如果今次 message 自己有新附件，就優先用今次新附件
- 如果今次 message 冇新附件，但 reply 咗舊文件，就 fallback 用被引用文件

## 常見 debug 方法

想 trace 某個 chat / update，通常會用以下幾招：

### 1. 打 health check

```bash
curl -i http://localhost:3000/health
```

### 2. 直接開 SQLite 睇資料

本地：

```bash
sqlite3 ./data/app.db
```

Dokku：

```bash
dokku run telegram-codex sqlite3 /app/data/app.db
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

### 3. 手動重設目前 chat session

喺 Telegram 對 bot 打：

```text
/new
```

如果你懷疑 memory / context 搞亂咗，呢個最快。

如果你懷疑係長期記憶記錯咗 user preference / 背景，而唔係 session context 爛咗，打：

```text
/forget
```

如果你唔想完全清走，而係想保留重點但瘦身，直接打：

```text
/summary
```

### 4. 睇 logs

本地：

```bash
./gradlew bootRun
```

Docker：

```bash
docker logs <container-id>
```

Dokku：

```bash
dokku logs telegram-codex -t
```

## 檢查

```bash
./gradlew test
```

如果你只想先 build jar：

```bash
./gradlew bootJar
```

### Docker

Docker image 會：

- build Spring Boot jar
- install `.codex-version` 指定嘅 `@openai/codex`
- 建立 `/app/data` 同 `/root/.codex`
- runtime 用 `java --enable-native-access=ALL-UNNAMED -jar /app/telegram-codex.jar`

```bash
docker build -t telegram-codex .
docker run --rm -p 3000:3000 \
  -e PORT=3000 \
  -e BASE_URL=https://your-domain.com \
  -e TELEGRAM_BOT_TOKEN=replace-me \
  -e TELEGRAM_WEBHOOK_SECRET=replace-me \
  -e SQLITE_DB_PATH=/app/data/app.db \
  -v "$(pwd)/data:/app/data" \
  -v "$HOME/.codex:/root/.codex" \
  telegram-codex
```

註冊 webhook：

```bash
docker exec <container-id> java --enable-native-access=ALL-UNNAMED \
  -jar /app/telegram-codex.jar \
  --spring.main.web-application-type=none \
  --app.task=telegram:set-webhook
```

更新 command menu：

```bash
docker exec <container-id> java --enable-native-access=ALL-UNNAMED \
  -jar /app/telegram-codex.jar \
  --spring.main.web-application-type=none \
  --app.task=telegram:update-commands
```

### Dokku

以下假設你個 app 叫 `telegram-codex`，domain 係 `telegram-codex.example.com`。

#### 1. 建 app 同 domain

```bash
dokku apps:create telegram-codex
dokku domains:set telegram-codex telegram-codex.example.com
```

如果你有開 HTTPS：

```bash
dokku letsencrypt:set telegram-codex email you@example.com
dokku letsencrypt:enable telegram-codex
```

#### 2. 準備 persistent storage

呢個 app 至少要 persist 兩樣：

- SQLite database：`/app/data`
- Codex auth / config：`/root/.codex`

先喺 host 開 directory：

```bash
sudo mkdir -p /var/lib/dokku/data/storage/telegram-codex/data
sudo mkdir -p /var/lib/dokku/data/storage/telegram-codex/codex
```

再 mount 入 container：

```bash
dokku storage:mount telegram-codex /var/lib/dokku/data/storage/telegram-codex/data:/app/data
dokku storage:mount telegram-codex /var/lib/dokku/data/storage/telegram-codex/codex:/root/.codex
```

如果你本身已經喺 server 登入過 Codex，可以直接將 auth 檔放入去 mount path：

```bash
sudo cp -R ~/.codex/. /var/lib/dokku/data/storage/telegram-codex/codex/
sudo chown -R 32767:32767 /var/lib/dokku/data/storage/telegram-codex/codex
sudo chown -R 32767:32767 /var/lib/dokku/data/storage/telegram-codex/data
```

#### 3. 設定環境變數

```bash
dokku config:set telegram-codex \
  PORT=3000 \
  BASE_URL=https://telegram-codex.example.com \
  TELEGRAM_BOT_TOKEN=replace-me \
  TELEGRAM_WEBHOOK_SECRET=replace-me \
  SQLITE_DB_PATH=/app/data/app.db \
  CODEX_EXEC_TIMEOUT_SECONDS=300
```

可選設定：

```bash
dokku config:set telegram-codex \
  ALLOWED_TELEGRAM_USER_IDS=123456789,987654321 \
  MAX_MEDIA_GROUP_IMAGES=6 \
  MAX_PDF_PAGES=4 \
  SESSION_TTL_DAYS=7 \
  MEDIA_GROUP_WAIT_MS=1200 \
  RATE_LIMIT_WINDOW_MS=10000 \
  RATE_LIMIT_MAX_MESSAGES=5
```

#### 4. Deploy

```bash
git remote add dokku dokku@your-server:telegram-codex
git push dokku main
```

#### 5. 註冊 webhook

```bash
dokku run telegram-codex bin/telegram-codex telegram:set-webhook
```

佢會將 webhook 設成 `${BASE_URL}/telegram/webhook`，所以 `BASE_URL` 唔好自己加 `/telegram/webhook`。

如要同步 Telegram command menu：

```bash
dokku run telegram-codex bin/telegram-codex telegram:update-commands
```

#### 6. 驗證

```bash
curl -i https://telegram-codex.example.com/health
dokku logs telegram-codex -t
```

如果 health check 正常，應該會見到：

```json
{"ok":true}
```

#### 7. 常用維護指令

重建 app：

```bash
dokku ps:rebuild telegram-codex
```

Backup SQLite：

```bash
sudo cp /var/lib/dokku/data/storage/telegram-codex/data/app.db \
       /var/lib/dokku/data/storage/telegram-codex/data/app.db.bak
```

## 測試

JUnit 覆蓋咗以下核心行為：

- health route
- webhook secret 驗證
- Telegram webhook controller failure path
- session TTL
- media group scheduling
- Telegram update parser
- 長期記憶注入 / 更新 / 清除
