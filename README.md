# telegram-codex

用 Telegram webhook 收訊息，之後喺 server 入面跑 `codex exec` 做回覆；對話狀態用 SQLite 存，HTTP layer 用 NestJS。

Demo：https://t.me/On99AppBot

## 功能

- 支援 Telegram 文字訊息
- 支援單張圖片同 caption
- 有 session memory
- 有 duplicate update 保護
- 有簡單 rate limit
- 可限制指定 Telegram user id

未支援：

- 多圖 message 一齊分析
- document 類型圖片
- 語音、影片、其他檔案

## 架構

- `src/server.ts`
  Nest app bootstrap
- `src/telegram`
  Telegram controller、webhook handler、message parser、Telegram API wrapper
- `src/conversation`
  對話流程、prompt、rate limiter、conversation types
- `src/storage`
  SQLite storage
- `src/config`
  env、logger、DI token、shared service contracts

## 需求

- Node.js `>=22`
- pnpm `>=10`
- 本機或 server 可以直接跑 `codex exec`
- `~/.codex/config.toml` 同 `~/.codex/auth.json` 已配置好

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
- install `@openai/codex`
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
- SQLite path 會喺 startup 時自動建立 folder
- logger 用官方 Nest `ConsoleLogger`，輸出係 JSON 格式

Dokku 官方 `certs:add` 支援直接由 tarball stdin 匯入，所以喺 Dokku server 跑：

```bash
cd ~/certs
dokku certs:add telegram-codex < on99.app.tar
```

之後確認證書狀態：

```bash
dokku certs:report telegram-codex
```

如果之後你換咗新 cert，可以再用：

```bash
cd ~/certs
dokku certs:update telegram-codex < on99.app.tar
```

注意：

- DNS 一定要已經指去你部 server
- `dokku domains:set telegram-codex telegram-codex.on99.app` 要先設好
- `http:80:3000` 同 `https:443:3000` port mapping 要先設好
- `BASE_URL` 仍然應該係 `https://telegram-codex.on99.app`
- 如果你個 `.crt` 仲有 CA bundle，要先將 cert 同 bundle 合併成一個 `.crt` 再入 tar

### 11. 確認 running container 入面有冇 `.codex` 檔案

如果 deploy 完之後 `codex exec` 失敗，先查 mount 有冇真係入到 container：

```bash
dokku enter telegram-codex web
ls -la /root/.codex
cat /root/.codex/config.toml
```

你應該要見到：

- `/root/.codex/config.toml`
- `/root/.codex/auth.json`
- `/root/.codex/AGENTS.md`（如果你有 sync）

### 12. 確認 webhook 狀態

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
dokku run telegram-codex node dist/src/scripts/setWebhook.js
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
dokku run telegram-codex node dist/src/scripts/setWebhook.js
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
