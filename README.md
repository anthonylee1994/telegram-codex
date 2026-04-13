# telegram-codex

用 Telegram webhook 收訊息，之後喺 server 入面跑 `codex exec` 做回覆；對話狀態用 SQLite 存，而家個 HTTP layer 用 Ruby on Rails API。

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
- 支援 `/start` 顯示 welcome / help message
- 支援 `/new` 重開當前 chat session
- 有 session memory
- 有 duplicate update 保護
- 有簡單 rate limit
- 可限制指定 Telegram user id

未支援：

- 多圖 message 一齊分析
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

主要 flow：

- `POST /telegram/webhook` 驗證 `X-Telegram-Bot-Api-Secret-Token`
- `TelegramUpdateParser` 將 Telegram payload 轉成 app message
- `TelegramWebhookHandler` 做 duplicate、防重送、allowed user、`/start`、`/new`、rate limit
- `ConversationService` 管 session TTL，同 `CodexCliClient` 串 `codex exec`
- `ChatSession` / `ProcessedUpdate` 用 SQLite 存狀態
- `rake telegram:set_webhook` 取代原本 script

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
| `RATE_LIMIT_WINDOW_MS` | rate limit window | `10000` |
| `RATE_LIMIT_MAX_MESSAGES` | window 內最多幾多訊息 | `5` |

## 本地設定

1. 複製 `.env.example` 做 `.env`
2. 填好 `TELEGRAM_BOT_TOKEN`
3. 填好 `BASE_URL`，例如 `https://your-domain.com`
4. 填好 `TELEGRAM_WEBHOOK_SECRET`
5. 如有需要再設 `ALLOWED_TELEGRAM_USER_IDS`
6. 安裝 gems

```bash
bundle install
```

7. 準備 database

```bash
bundle exec rails db:prepare
```

## 開發

```bash
bundle exec rails server -p 3000
```

主要 endpoint：

- `GET /health`
- `POST /telegram/webhook`

## Telegram commands

- `/start`：顯示 welcome / help message
- `/new`：清除當前 chat 嘅 session memory，下一句重新開始

平時直接 send 文字或者圖片畀 bot 就得，唔需要 command。

## 註冊 Telegram webhook

```bash
bundle exec rake telegram:set_webhook
```

佢會將 webhook 設成：

```text
${BASE_URL}/telegram/webhook
```

所以 `BASE_URL` 唔好自己加 `/telegram/webhook`。

## Rails console

```bash
bundle exec rails console
```

例如你可以直接睇 session / processed update：

```ruby
ChatSession.all
ProcessedUpdate.order(update_id: :desc).limit(10)
```

## 檢查

```bash
bundle exec rails zeitwerk:check
bundle exec rubocop -A
bundle exec rubocop
bundle exec rspec
```

如果你仲想順手整理 Markdown / Dockerfile，可以另外跑：

```bash
prettier --write README.md .env.example Dockerfile .gitignore .dockerignore
```

## Docker

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

## Dokku 部署

以下假設你個 app 叫 `telegram-codex`，domain 係 `telegram-codex.example.com`。

### 1. 建 app 同 domain

```bash
dokku apps:create telegram-codex
dokku domains:set telegram-codex telegram-codex.example.com
```

如果你有開 HTTPS，記得另外處理 certificate，例如：

```bash
dokku letsencrypt:set telegram-codex email you@example.com
dokku letsencrypt:enable telegram-codex
```

### 2. 準備 persistent storage

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

如果你本身已經喺 server 登入過 Codex，可以直接將 auth 檔放入去 mount path；如果未有，就喺 host 準備：

```bash
sudo cp -R ~/.codex/. /var/lib/dokku/data/storage/telegram-codex/codex/
sudo chown -R 32767:32767 /var/lib/dokku/data/storage/telegram-codex/codex
```

SQLite directory 都建議一樣俾返 container user 可寫：

```bash
sudo chown -R 32767:32767 /var/lib/dokku/data/storage/telegram-codex/data
```

### 3. 設定環境變數

```bash
dokku config:set telegram-codex \
  RAILS_ENV=production \
  PORT=3000 \
  BASE_URL=https://telegram-codex.example.com \
  TELEGRAM_BOT_TOKEN=replace-me \
  TELEGRAM_WEBHOOK_SECRET=replace-me \
  SQLITE_DB_PATH=/rails/data/app.db \
  SESSION_TTL_DAYS=7 \
  RATE_LIMIT_WINDOW_MS=10000 \
  RATE_LIMIT_MAX_MESSAGES=5
```

如要限制指定 Telegram user：

```bash
dokku config:set telegram-codex ALLOWED_TELEGRAM_USER_IDS=123456789,987654321
```

### 4. Deploy

如果你用 git push deploy：

```bash
git remote add dokku dokku@your-server:telegram-codex
git push dokku main
```

如果你 branch 唔係 `main`，改返你自己嗰個 branch 名。

app 起動時會自動跑 `bundle exec rails db:prepare`，所以正常唔使手動 migrate。

### 5. 註冊 Telegram webhook

第一次 deploy 完，或者之後改咗 domain / `BASE_URL`，要喺 Dokku app 入面跑：

```bash
dokku run telegram-codex bundle exec rake telegram:set_webhook
```

### 6. 驗證

```bash
curl -i https://telegram-codex.example.com/health
dokku logs telegram-codex -t
```

如果 health check 正常，應該會見到：

```json
{"ok":true}
```

### 7. 常用維護指令

重建 app：

```bash
dokku ps:rebuild telegram-codex
```

開 Rails console：

```bash
dokku run telegram-codex bundle exec rails console
```

手動睇最近 update：

```ruby
ProcessedUpdate.order(update_id: :desc).limit(10)
```

如果想 backup SQLite：

```bash
sudo cp /var/lib/dokku/data/storage/telegram-codex/data/app.db /var/lib/dokku/data/storage/telegram-codex/data/app.db.bak
```

## 測試

RSpec 覆蓋咗以下核心行為：

- health route
- webhook secret 驗證
- webhook handler failure path
- session TTL
- pending reply replay
- Telegram update parser
