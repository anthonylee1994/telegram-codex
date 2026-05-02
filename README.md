# telegram-codex

Telegram bot backend，用 pnpm + NestJS 跑 webhook、session、長期記憶、rate limit，同埋 call `codex exec` 生成回覆。

## 功能

- 支持 Telegram 文字、圖片、caption、相簿多圖。
- 支持 reply 舊文字或者舊圖片延續上下文。
- 支持 `/start`、`/help`、`/status`、`/session`、`/memory`、`/compact`、`/forget`、`/new`。
- `/compact` 會非同步壓縮目前 session，再主動 send 摘要返 Telegram。
- SQLite + TypeORM 儲存 session、memory、processed updates、media group buffer。
- duplicate update 保護、簡單 rate limit、可限制指定 Telegram user id。
- 手動 task 用 `nest-commander`。

## 環境變數

複製 `.env.example` 做 `.env`，至少填：

- `BASE_URL`
- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_WEBHOOK_SECRET`

預設 SQLite 路徑係 `./data/app.db`。

## 本地開發

```bash
pnpm install
pnpm run dev
```

常用 endpoint：

- `GET /health`
- `POST /telegram/webhook`

手動 task：

```bash
pnpm run task telegram:set-webhook
pnpm run task telegram:update-commands
```

## 驗證

```bash
pnpm run typecheck
pnpm run test
pnpm run prettier:check
pnpm run build
```

## Dokku

repo 內有 `Dockerfile` 同 `Procfile`。部署前要設定 env，同埋將 `/app/data` 掛 persistent storage，否則 SQLite DB 會喺 container 重建時消失。

Codex CLI auth 亦要喺 runtime container 入面存在，例如掛載 `/root/.codex`。
