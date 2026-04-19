# telegram-codex

用 Telegram webhook 收訊息，之後 enqueue background job 跑 `codex exec` 做回覆；對話狀態用 SQLite 存，而家個 HTTP layer 用 Ruby on Rails API。

Demo：https://t.me/On99AppBot

## 點解會整呢個 project

呢個 project 其實係由一個幾實際嘅需求出發：

- 想隨時隨地用 Codex 幫手
- 唔想另外再砌一套 OpenAI API key integration
- 想保留基本對話記錄，而唔係每次都由零開始

所以最後就變成一個 Telegram bot backend：

- Telegram 負責做最順手嘅輸入入口
- server 負責收 webhook、做 session memory、控 rate limit
- `codex exec` 負責真正生成回覆

簡單講，呢個 project 係想將本機用緊嘅 Codex CLI，包成一個可以長期運行、日常真係用得着嘅 bot，而唔係淨係做 demo。

## 功能

- 支援 Telegram 文字訊息
- 支援單張圖片同 caption
- 支援 Telegram 相簿多圖訊息分析
- 支援 `/start` 顯示 welcome / help message
- 支援 `/new` 重開當前 chat session
- 支援最多 3 個 reply keyboard suggested replies
- 有 session memory
- 有 duplicate update 保護
- 有簡單 rate limit
- 可限制指定 Telegram user id

未支援：

- document 類型圖片
- 語音、影片、其他檔案

## Rails API 架構

```text
app/
├── controllers/
│   ├── health_controller.rb
│   └── telegram_webhooks_controller.rb
├── models/
│   ├── chat_session.rb
│   └── processed_update.rb
└── services/
    ├── app_config.rb
    ├── chat_rate_limiter.rb
    ├── codex_cli_client.rb
    ├── conversation_service.rb
    ├── telegram_client.rb
    ├── telegram_update_parser.rb
    └── telegram_webhook_handler.rb
db/
├── migrate/
│   ├── create_chat_sessions.rb
│   └── create_processed_updates.rb
└── schema.rb
lib/tasks/
└── telegram.rake
spec/
└── ...
```

呢個 repo 其實幾刻意咁保持薄 controller、厚 service：

- controller 只處理 HTTP request / response
- parser 專心將 Telegram payload 轉成 app 可以用嘅格式
- service 負責業務邏輯
- model 只做 persistence，唔塞太多 business rule

咁做嘅好處係：

- webhook flow 容易測試
- `codex exec` integration 唔會同 HTTP 層綁死
- SQLite / Telegram / Codex 三個依賴點都分得比較開

## Request Flow

由 Telegram 打入嚟，到 bot 回覆，用緊以下條 path：

- `POST /telegram/webhook` 驗證 `X-Telegram-Bot-Api-Secret-Token`
- `TelegramUpdateParser` 將 Telegram payload 轉成 app message
- `TelegramWebhookHandler` 做 duplicate、防重送、allowed user、`/start`、`/new`、rate limit
- `ConversationService` 管 session TTL，同 `CodexCliClient` 串 `codex exec`
- `ReplyGenerationJob` 非同步生成回覆同發送 Telegram 訊息
- `ChatSession` / `ProcessedUpdate` 用 SQLite 存狀態
- `rake telegram:set_webhook` 取代原本 script

拆開少少講：

1. Telegram webhook 打入 Rails。
2. `TelegramWebhooksController` 先驗證 secret token。
3. 驗證通過後，controller 將 payload 交畀 `TelegramWebhookHandler`。
4. handler 會：
   - parse 文字 / 圖片 message
   - 檢查係咪 duplicate update
   - 檢查係咪 pending reply replay
   - 檢查 allowed users
   - 處理 `/start` 同 `/new`
   - 做 chat-level rate limit
5. 真正要生成回覆時，handler 會 enqueue `ReplyGenerationJob`，Webhook 先即刻回 `200 OK`。
6. `ReplyGenerationJob` 再 call `ConversationService` 攞返目前 chat 嘅 session state。
7. `ConversationService` call `CodexCliClient`。
8. `CodexCliClient` 用 transcript + system prompt 組 prompt，然後跑 `codex exec`。
9. `CodexCliClient` 會再生成 3 個 suggested replies。
10. job 將主答案同 suggested replies send 番 Telegram，並將新 session state 寫返 SQLite。

## 主要檔案點運作

下面呢段係比你開 repo 想快速認路用。

### HTTP 層

- [`application_controller.rb`](app/controllers/application_controller.rb)
  - Rails API base controller，而家冇塞 logic，純粹做入口底座。

- [`health_controller.rb`](app/controllers/health_controller.rb)
  - 提供 `GET /health`。
  - 主要比 load balancer、Dokku、自己 curl check service 仲生勾勾。

- [`telegram_webhooks_controller.rb`](app/controllers/telegram_webhooks_controller.rb)
  - Telegram webhook 真入口。
  - 只做三件事：驗 secret、call handler、將結果轉成 HTTP status。
  - 回覆生成已經唔喺 request thread 做，webhook 主要負責快速 ack Telegram。
  - 呢層刻意唔做重業務邏輯，因為 webhook flow 測試會乾淨好多。

### Model / Persistence

- [`chat_session.rb`](app/models/chat_session.rb)
  - 每個 Telegram chat 一條 row。
  - 存 `last_response_id` 同 `updated_at`。
  - 作用係畀下次 `codex exec` 知道上一輪對話狀態。
  - `/new` 會清走目前 chat 嘅 session。
  - session 過期唔係靠 cron，而係下次個 chat 再講嘢時 lazy cleanup。

- [`processed_update.rb`](app/models/processed_update.rb)
  - 用 Telegram `update_id` 做主鍵。
  - 主要係防 duplicate webhook，仲有 pending reply replay。
  - 如果 reply 已生成但 send Telegram 失敗，下次 Telegram resend 同一個 update，app 可以直接補送主答案同 suggested replies，唔使再 call 一次 Codex。

### Service 層

- [`app_config.rb`](app/services/app_config.rb)
  - 將 ENV parse 成 app 用嘅 config object。
  - 同時會確保 SQLite directory 存在。
  - 呢層等你唔使到處直接 `ENV.fetch`。

- [`telegram_update_parser.rb`](app/services/telegram_update_parser.rb)
  - 將 Telegram 原始 payload 轉成 app 內部用嘅 hash。
  - 支援文字訊息、單張圖片同 Telegram 相簿訊息。
  - 每張圖都會揀 Telegram `photo` array 裏面最大嗰張。

- [`chat_rate_limiter.rb`](app/services/chat_rate_limiter.rb)
  - in-memory rate limiter。
  - 粒度係 `chat_id`，唔係 global。
  - 適合單機、小流量 bot；如果之後多 instances，就要改成 shared store。

- [`telegram_client.rb`](app/services/telegram_client.rb)
  - 包住 Telegram Bot API。
  - 主要做：
    - send message
    - send / remove reply keyboard
    - send typing action
    - download image 到 temp file
    - set webhook
  - 仲會順手做 Telegram HTML formatting，令 code block / inline code 顯示得正常啲。

- [`reply_generation_job.rb`](app/jobs/reply_generation_job.rb)
  - 將原本同步 reply generation flow 搬去 background job。
  - webhook claim 咗個 update 之後，就由呢個 job 負責真正生成 reply、send Telegram，同埋 pending reply replay。

- [`conversation_service.rb`](app/services/conversation_service.rb)
  - 對話層 orchestration。
  - 主要責任：
    - 讀寫 `ChatSession`
    - 讀寫 `ProcessedUpdate`
    - 判斷 session TTL
    - call `CodexCliClient`
    - opportunistic prune 舊 `ProcessedUpdate`
  - 如果你想搵「個 bot 記憶點樣管理」，通常由呢個 file 開始睇最啱。

- [`codex_cli_client.rb`](app/services/codex_cli_client.rb)
  - 真正同 `codex exec` 接軌嗰層。
  - 佢會：
    - parse 上次 conversation state
    - 先同 system prompt 拼埋做 prompt 生成主答案
    - 再用更新後 transcript 生成 suggested replies
    - 如果有圖就加 `--image`
    - 讀返 `codex exec --output-last-message` 生成嘅最後訊息
  - `codex exec` 仍然係同步 call，但而家係喺 background job 入面跑，唔會再頂住 webhook request。

- [`telegram_webhook_handler.rb`](app/services/telegram_webhook_handler.rb)
  - 成個 bot flow 最核心嘅 file。
  - 大部分 Telegram 行為都喺呢度：
    - unsupported message fallback
    - duplicate ignore
    - pending reply replay
    - unauthorized user reject
    - `/start`
    - `/new`
    - rate limit
    - typing indicator
    - media group aggregation
    - reply keyboard suggested replies
    - 圖片 download + cleanup
    - generic error fallback

### Config / Tasks

- [`routes.rb`](config/routes.rb)
  - 只 expose 兩個 endpoint：
    - `GET /health`
    - `POST /telegram/webhook`

- [`database.yml`](config/database.yml)
  - production 用 app DB + Solid Queue DB 嘅 SQLite multi-db 配置。
  - app DB 預設 path 由 `SQLITE_DB_PATH` 控；queue DB 預設 path 由 `SOLID_QUEUE_DB_PATH` 控。

- [`queue.yml`](config/queue.yml)
  - Solid Queue worker / dispatcher 設定。
  - 而家預設會跑全部 queues。

- [`Procfile`](Procfile)
  - `web` 用 Puma 起 Rails API。
  - `worker` 用 `bundle exec bin/jobs` 起 Solid Queue worker。

- [`development.rb`](config/environments/development.rb)
  - development 保持用 `:async` job adapter，唔使另外開 Solid Queue worker 都可以本機試 flow。

- [`production.rb`](config/environments/production.rb)
  - production 用 `:solid_queue`，並連去 `queue` database。

- [`telegram.rake`](lib/tasks/telegram.rake)
  - `bundle exec rake telegram:set_webhook`
  - 用而家 ENV 裏面個 `BASE_URL` 直接註冊 Telegram webhook。
  - `bundle exec rake telegram:update_commands`
  - 將 `/start` 同 `/new` command description 同步去 Telegram bot menu。

- [`auto_annotate_models.rake`](lib/tasks/auto_annotate_models.rake)
  - 同 schema comments / annotate model 有關。
  - 純粹係維護工具，唔影響 runtime。

### Tests

- [`app_spec.rb`](spec/requests/app_spec.rb)
  - 驗 HTTP 層：`/health` 同 webhook secret 驗證。

- [`conversation_service_spec.rb`](spec/services/conversation_service_spec.rb)
  - 驗 session TTL、reply generation input、processed update prune。

- [`telegram_update_parser_spec.rb`](spec/services/telegram_update_parser_spec.rb)
  - 驗 Telegram payload parsing。

- [`telegram_webhook_handler_spec.rb`](spec/services/telegram_webhook_handler_spec.rb)
  - 驗 pending reply replay 呢種最易出事嘅邊界情況。

## 資料點存

SQLite 而家主要得兩張表：

- `chat_sessions`
  - 每個 chat 一條 session state
  - 用嚟保留對話上下文

- `processed_updates`
  - 每個 Telegram `update_id` 一條處理記錄
  - 用嚟防 duplicate，同埋保留 pending reply replay 所需資料

實際 lifecycle 係：

- `ChatSession`
  - `/new` 會清
  - 過咗 `SESSION_TTL_DAYS` 會喺下次用到嗰個 chat 時被清

- `ProcessedUpdate`
  - reply send 成功後會保留
  - `ConversationService` 會 opportunistic 清走已送出而且夠舊嘅 records
  - 唔需要額外 cron job 都可以慢慢瘦身

## 需求

- Ruby `4.0.2`
- Bundler `4.x`
- SQLite 3
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
| `SESSION_TTL_DAYS` | session 過期日數 | `7` |
| `MEDIA_GROUP_WAIT_MS` | Telegram 相簿多圖聚合等待時間 | `1200` |
| `RATE_LIMIT_WINDOW_MS` | rate limit window | `10000` |
| `RATE_LIMIT_MAX_MESSAGES` | window 內最多幾多訊息 | `5` |

## 部署

### 本地開發

1. 複製 `.env.example` 做 `.env`
2. 填好環境變數：
   - `TELEGRAM_BOT_TOKEN`
   - `BASE_URL`（例如 `https://your-domain.com`）
   - `TELEGRAM_WEBHOOK_SECRET`
   - `ALLOWED_TELEGRAM_USER_IDS`（可選）
3. 安裝 gems

```bash
bundle install
```

4. 準備 database

```bash
bundle exec rails db:prepare
```

5. 啟動 server

```bash
bundle exec rails server -p 3000
```

6. 註冊 webhook

```bash
bundle exec rake telegram:set_webhook
```

主要 endpoint：

- `GET /health`
- `POST /telegram/webhook`

## Telegram commands

- `/start`：顯示 welcome / help message，同時清走而家個 reply keyboard
- `/new`：清除當前 chat 嘅 session memory，下一句重新開始，同時清走而家個 reply keyboard

平時直接 send 文字或者圖片畀 bot 就得，唔需要 command。撳 suggested reply 會由 Telegram client 直接送出一條新 message，所以 chat 入面會見到自己嘅綠色訊息。

## 常見 debug 方法

想 trace 某個 chat／update，通常會用以下幾招：

### 1. 用 Rails console 睇資料

本地：

```bash
bundle exec rails console
```

Dokku：

```bash
dokku run telegram-codex bundle exec rails console
```

常用查詢：

```ruby
# 睇特定 chat session
ChatSession.find_by(chat_id: "123456")

# 睇特定 update
ProcessedUpdate.find_by(update_id: 123456789)

# 睇最近 updates
ProcessedUpdate.order(update_id: :desc).limit(20)

# 睇所有 sessions
ChatSession.all
```

### 2. 本地打 health check

```bash
curl -i http://localhost:3000/health
```

### 3. 手動重設目前 chat session

喺 Telegram 對 bot 打：

```text
/new
```

如果你懷疑 memory / context 搞亂咗，呢個最快。

### 4. 睇 logs

本地：

```bash
bundle exec rails server
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
bundle exec rails zeitwerk:check
bundle exec rubocop -A
bundle exec rubocop
bundle exec rspec
```

### Docker

Docker image 會：

- install production gems
- install `.codex-version` 指定嘅 `@openai/codex`
- 建立 `/rails/data` 同 `/root/.codex`
- startup 時自動 `rails db:prepare`

```bash
docker build -t telegram-codex .
docker run --rm -p 3000:3000 \
  -e PORT=3000 \
  -e BASE_URL=https://your-domain.com \
  -e TELEGRAM_BOT_TOKEN=replace-me \
  -e TELEGRAM_WEBHOOK_SECRET=replace-me \
  -e SQLITE_DB_PATH=/rails/data/app.db \
  -v $(pwd)/data:/rails/data \
  -v $HOME/.codex:/root/.codex \
  telegram-codex
```

註冊 webhook：

```bash
docker exec <container-id> bundle exec rake telegram:set_webhook
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

- SQLite database：`/rails/data`
- Codex auth / config：`/root/.codex`

先喺 host 開 directory：

```bash
sudo mkdir -p /var/lib/dokku/data/storage/telegram-codex/data
sudo mkdir -p /var/lib/dokku/data/storage/telegram-codex/codex
```

再 mount 入 container：

```bash
dokku storage:mount telegram-codex /var/lib/dokku/data/storage/telegram-codex/data:/rails/data
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
  RAILS_ENV=production \
  PORT=3000 \
  BASE_URL=https://telegram-codex.example.com \
  TELEGRAM_BOT_TOKEN=replace-me \
  TELEGRAM_WEBHOOK_SECRET=replace-me \
  SQLITE_DB_PATH=/rails/data/app.db
```

可選設定（如要限制指定 user 或調整 rate limit）：

```bash
dokku config:set telegram-codex \
  ALLOWED_TELEGRAM_USER_IDS=123456789,987654321 \
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

app 起動時會自動跑 `bundle exec rails db:prepare`。

#### 5. 註冊 webhook

```bash
dokku run telegram-codex bundle exec rake telegram:set_webhook
```

佢會將 webhook 設成 `${BASE_URL}/telegram/webhook`，所以 `BASE_URL` 唔好自己加 `/telegram/webhook`。

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

開 Rails console：

```bash
dokku run telegram-codex bundle exec rails console
```

Backup SQLite：

```bash
sudo cp /var/lib/dokku/data/storage/telegram-codex/data/app.db \
       /var/lib/dokku/data/storage/telegram-codex/data/app.db.bak
```

## 測試

RSpec 覆蓋咗以下核心行為：

- health route
- webhook secret 驗證
- webhook handler failure path
- session TTL
- pending reply replay
- reply keyboard suggested replies
- Telegram update parser
