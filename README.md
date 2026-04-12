# telegram-codex

用 Telegram webhook 收訊息，之後喺 server 入面跑 `codex exec` 做回覆；對話狀態用 SQLite 存，HTTP layer 用 NestJS。

Demo：https://t.me/On99AppBot

## 點解會寫呢個 project

呢個 project 係由一個幾實際嘅需求出發：

- 我想隨時隨地用 Codex 幫手
- 但唔想另外砌一套 OpenAI API key flow
- 我又想保留基本對話記錄，而唔係每次都由零開始

所以最後就寫咗呢個 app，將 Codex CLI 包成一個 Telegram bot backend：

- Telegram 負責做最順手嘅輸入入口
- server 負責收 webhook、做 session memory、控 rate limit
- `codex exec` 負責真正生成回覆

簡單講，呢個 project 係想將「本機用緊嘅 Codex CLI」變成一個可以長期運行、自己日常真係會用到嘅 bot，而唔係淨係做 demo。

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

## NestJS 架構

```text
src/
├── server.ts
│   └── app bootstrap
│       - NestFactory.create(AppModule)
│       - 接上 AppLogger
│       - 讀 APP_ENV
│       - listen PORT
├── app.module.ts
│   └── root module
│       ├── imports ConfigModule
│       ├── imports HealthModule
│       └── imports TelegramModule
├── config/
│   ├── config.module.ts
│   │   └── global providers
│   │       ├── APP_ENV
│   │       ├── AppLogger
│   │       └── LOGGER -> AppLogger
│   ├── env.ts
│   │   └── env parse 同 validation
│   ├── logger.ts
│   │   └── Nest ConsoleLogger wrapper + scoped logger
│   ├── tokens.ts
│   │   └── DI tokens
│   └── service.types.ts
│       └── shared service contracts
├── health/
│   ├── health.module.ts
│   └── health.controller.ts
│       └── GET /health
├── telegram/
│   ├── telegram.module.ts
│   │   └── Telegram flow module
│   │       ├── TelegramController
│   │       ├── TelegramWebhookHandler
│   │       ├── TelegramUpdateParser
│   │       ├── TelegramService
│   │       └── ChatRateLimiter
│   ├── telegram.controller.ts
│   │   └── POST /telegram/webhook
│   ├── telegram-webhook-handler.service.ts
│   │   └── webhook 主流程
│   │       - 驗證訊息格式
│   │       - 檢查 allowed users
│   │       - 檢查 duplicate update
│   │       - rate limit
│   │       - call ConversationService
│   ├── telegram-update-parser.service.ts
│   │   └── Telegram update -> IncomingTelegramMessage
│   └── telegram.service.ts
│       └── Telegram Bot API wrapper
├── conversation/
│   ├── conversation.module.ts
│   │   └── conversation module
│   │       ├── ConversationService
│   │       ├── CodexCliClient
│   │       └── REPLY_CLIENT -> CodexCliClient
│   ├── conversation.service.ts
│   │   └── session memory + reply orchestration
│   ├── rate-limiter.service.ts
│   │   └── in-memory chat rate limiter
│   ├── prompts.ts
│   │   └── system prompt
│   └── conversation.types.ts
│       └── conversation domain types
├── storage/
│   ├── storage.module.ts
│   │   └── persistence module
│   │       ├── SqliteStorage
│   │       ├── SESSION_REPOSITORY -> SqliteStorage
│   │       └── PROCESSED_UPDATE_REPOSITORY -> SqliteStorage
│   └── sqlite.service.ts
│       └── SQLite implementation
└── scripts/
    └── set-webhook.ts
        └── 用 AppModule context 註冊 Telegram webhook
```

DI 主要分兩類：

- class provider：例如 `TelegramService`、`ConversationService`
- token provider：例如 `APP_ENV`、`LOGGER`、`SESSION_REPOSITORY`、`PROCESSED_UPDATE_REPOSITORY`、`REPLY_CLIENT`

咁做係為咗將 implementation 同 contract 分開，尤其係 env、logger、repository 呢類唔適合直接靠 TypeScript interface inject 嘅依賴。

## 需求

- Node.js `>=22`
- pnpm `>=10`
- 本機或 server 可以直接跑 `codex exec`
- `~/.codex/config.toml` 同 `~/.codex/auth.json` 已配置好
- 本機最好用 repo 根目錄 [` .codex-version `] 所指定嗰個 Codex CLI 版本

## 環境變數

| 變數                        | 用途                                | 預設值          |
| --------------------------- | ----------------------------------- | --------------- |
| `PORT`                      | HTTP port                           | `3000`          |
| `BASE_URL`                  | 對外 base URL，用嚟註冊 webhook     | 無              |
| `TELEGRAM_BOT_TOKEN`        | Telegram bot token                  | 無              |
| `TELEGRAM_WEBHOOK_SECRET`   | Telegram webhook secret header      | 無              |
| `ALLOWED_TELEGRAM_USER_IDS` | 限定可用 Telegram user id，逗號分隔 | 空              |
| `SQLITE_DB_PATH`            | SQLite database path                | `./data/app.db` |
| `SESSION_TTL_DAYS`          | session 過期日數                    | `7`             |
| `RATE_LIMIT_WINDOW_MS`      | rate limit window                   | `10000`         |
| `RATE_LIMIT_MAX_MESSAGES`   | window 內最多幾多訊息               | `5`             |

## 本地設定

1. 複製 `.env.example` 做 `.env`
2. 填好 `TELEGRAM_BOT_TOKEN`
3. 填好 `BASE_URL`，例如 `https://your-domain.com`
4. 填好 `TELEGRAM_WEBHOOK_SECRET`
5. 如有需要再設 `ALLOWED_TELEGRAM_USER_IDS`
6. 安裝依賴

```bash
pnpm install
```

## 開發

```bash
pnpm dev
```

server 會 listen `PORT`，主要 endpoint：

- `GET /health`
- `POST /telegram/webhook`

## Telegram commands

- `/start`：顯示 welcome / help message
- `/new`：清除當前 chat 嘅 session memory，下一句重新開始

平時直接 send 文字或者圖片畀 bot 就得，唔需要 command。

## 註冊 Telegram webhook

```bash
pnpm set-webhook
```

佢會將 webhook 設成：

```text
${BASE_URL}/telegram/webhook
```

所以 `BASE_URL` 唔好自己加 `/telegram/webhook`。

## 檢查

```bash
pnpm type-check
pnpm lint
pnpm format
pnpm test
```

## Build 同 run

```bash
pnpm build
pnpm start
```

build output 會去 `dist/`，production entry 係：

```text
dist/src/server.js
```

## Docker

Docker image 會：

- build TypeScript output
- install production dependencies
- install `.codex-version` 指定嘅 `@openai/codex`
- 建立 `/app/data` 同 `/root/.codex`

```bash
docker build -t telegram-codex .
docker run --rm -p 3000:3000 \
  -e PORT=3000 \
  -e BASE_URL=https://your-domain.com \
  -e TELEGRAM_BOT_TOKEN=replace-me \
  -e TELEGRAM_WEBHOOK_SECRET=replace-me \
  -e SQLITE_DB_PATH=/app/data/app.db \
  -v $(pwd)/data:/app/data \
  -v $HOME/.codex:/root/.codex \
  telegram-codex
```

## Dokku 部署

假設 app 叫 `telegram-codex`。

### 1. 建 app 同 domain

```bash
dokku apps:create telegram-codex
dokku domains:set telegram-codex telegram-codex.on99.app
```

### 2. 準備 persistent storage

需要 mount：

- `/app/data`
- `/root/.codex`

```bash
sudo mkdir -p /var/lib/dokku/data/storage/telegram-codex/data
sudo mkdir -p /var/lib/dokku/data/storage/telegram-codex/codex

dokku storage:mount telegram-codex /var/lib/dokku/data/storage/telegram-codex/data:/app/data
dokku storage:mount telegram-codex /var/lib/dokku/data/storage/telegram-codex/codex:/root/.codex
```

### 3. 放入 Codex 認證

最少要有：

- `/root/.codex/config.toml`
- `/root/.codex/auth.json`

如果你要 container 都食到額外 `AGENTS.md`，可以一齊 mount 埋。

### 4. 設定 env

```bash
dokku config:set telegram-codex \
  NODE_ENV=production \
  PORT=3000 \
  BASE_URL=https://telegram-codex.on99.app \
  TELEGRAM_BOT_TOKEN=replace-me \
  TELEGRAM_WEBHOOK_SECRET=replace-me \
  ALLOWED_TELEGRAM_USER_IDS= \
  SQLITE_DB_PATH=/app/data/app.db \
  SESSION_TTL_DAYS=7 \
  RATE_LIMIT_WINDOW_MS=10000 \
  RATE_LIMIT_MAX_MESSAGES=5
```

### 5. Deploy

```bash
git push dokku HEAD:main
```

### 6. 註冊 webhook

deploy 完之後：

```bash
dokku run telegram-codex node dist/src/scripts/set-webhook.js
```

### 7. 睇 log

```bash
dokku logs telegram-codex -t
```

## 備註

- app 依賴本機或 server 已登入嘅 Codex CLI，唔係 OpenAI API key flow
- Docker 會跟 `.codex-version` 安裝固定 Codex CLI 版本，建議你本機都跟返同一版
- SQLite path 會喺 startup 時自動建立 folder
- logger 用官方 Nest `ConsoleLogger` 做底層實作，並加咗 app-specific wrapper 同 scoped context

deploy 完之後，任何地方都可以跑：

```bash
curl "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getWebhookInfo"
```

你要見到：

- `url` 係 `https://telegram-codex.on99.app/telegram/webhook`
- 最近冇 `last_error_message`

## 常見問題

### `bad webhook: An HTTPS URL must be provided for webhook`

即係你個 `BASE_URL` 錯咗。改返做：

```bash
https://telegram-codex.on99.app
```

之後再跑：

```bash
dokku config:set telegram-codex BASE_URL=https://telegram-codex.on99.app
dokku run telegram-codex node dist/src/scripts/set-webhook.js
```

### `Rejected Telegram webhook request with invalid secret`

通常係以下其中一樣：

- `TELEGRAM_WEBHOOK_SECRET` 改過，但 webhook 未重新註冊
- app restart 之後食咗另一個 env value
- Telegram 仲打緊舊 deployment

修法：

```bash
dokku config:set telegram-codex TELEGRAM_WEBHOOK_SECRET=your-secret
dokku ps:rebuild telegram-codex
dokku run telegram-codex node dist/src/scripts/set-webhook.js
```

### deploy 完顯示 `http://telegram-codex.on99.app:3000`

呢個通常唔係 app code 問題，係 Dokku 對外 port mapping 未設好。

喺 Dokku server 跑：

```bash
dokku ports:set telegram-codex http:80:3000 https:443:3000
dokku ports:report telegram-codex
```

之後你對外應該只用：

```bash
https://telegram-codex.on99.app
```

### 自訂 TLS cert 加唔到

通常係以下其中一樣：

- tar 入面冇 `.crt` / `.key`
- cert 同 domain 唔匹配
- `80/443 -> 3000` port mapping 未設好
- `domains:set` 未設好

可以先查：

```bash
dokku domains:report telegram-codex
dokku ports:report telegram-codex
dokku certs:report telegram-codex
```

### production 上面 `codex exec` 失敗

通常係以下其中一樣：

- `/root/.codex/config.toml` 唔存在
- `/root/.codex/auth.json` 唔存在
- mount path 錯咗
- auth file 內容唔啱

可以咁查：

```bash
dokku enter telegram-codex web
ls -la /root/.codex
codex --version
```
