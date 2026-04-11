# telegram-codex

用 Codex CLI 做回覆、用 SQLite 存 session memory 嘅 Telegram bot backend。

## 本地設定

1. 將 `.env.example` 複製做 `.env`
2. 用 BotFather 開一個 bot，填返 `TELEGRAM_BOT_TOKEN`
3. 填好 `BASE_URL`、`TELEGRAM_WEBHOOK_SECRET` 同 `ALLOWED_TELEGRAM_USER_IDS`（多個 id 用逗號分隔）
4. 確保部機可以用現有 `~/.codex/config.toml` 同 `~/.codex/auth.json` 跑到 `codex exec`
5. 安裝 dependencies：

```bash
pnpm install
```

## 本地開發

```bash
pnpm dev
```

## 設定 webhook

`pnpm set-webhook` 會直接讀 `.env` 入面嘅 `BASE_URL`，所以你跑之前要先確認 `BASE_URL` 係啱。

```bash
pnpm set-webhook
```

## 檢查指令

```bash
pnpm type-check
pnpm lint
pnpm format
pnpm test
```

## Dokku 部署

以下假設：

- Dokku app 名叫 `telegram-codex`
- Domain 係 `telegram-codex.on99.app`
- Dokku server user 係 `dokku`

### 1. 建 app

喺 Dokku server 跑：

```bash
dokku apps:create telegram-codex
dokku domains:set telegram-codex telegram-codex.on99.app
```

### 2. 準備 persistent storage

呢個 project 需要兩個 persistent mount：

- `/app/data`：俾 SQLite 用
- `/root/.codex`：俾 `codex exec` 讀 auth/config 用

喺 Dokku server 跑：

```bash
sudo mkdir -p /var/lib/dokku/data/storage/telegram-codex/data
sudo mkdir -p /var/lib/dokku/data/storage/telegram-codex/codex

dokku storage:mount telegram-codex /var/lib/dokku/data/storage/telegram-codex/data:/app/data
dokku storage:mount telegram-codex /var/lib/dokku/data/storage/telegram-codex/codex:/root/.codex
```

### 3. 將 Codex 認證檔放上 server

你一定要將以下兩個 file 放入 Dokku storage mount：

- `config.toml`
- `auth.json`

喺你本機跑：

```bash
scp ~/.codex/config.toml dokku@your-server:/tmp/config.toml
scp ~/.codex/auth.json dokku@your-server:/tmp/auth.json
```

之後 SSH 入 server，再搬去正確位置：

```bash
sudo mv /tmp/config.toml /var/lib/dokku/data/storage/telegram-codex/codex/config.toml
sudo mv /tmp/auth.json /var/lib/dokku/data/storage/telegram-codex/codex/auth.json
sudo chown -R dokku:dokku /var/lib/dokku/data/storage/telegram-codex
```

最後喺 server 上面應該會有：

```bash
/var/lib/dokku/data/storage/telegram-codex/codex/config.toml
/var/lib/dokku/data/storage/telegram-codex/codex/auth.json
```

入到 container 入面之後，會對應成：

```bash
/root/.codex/config.toml
/root/.codex/auth.json
```

### 4. 設 app config

喺 Dokku server 跑：

```bash
dokku config:set telegram-codex \
  NODE_ENV=production \
  PORT=3000 \
  BASE_URL=https://telegram-codex.on99.app \
  TELEGRAM_BOT_TOKEN=replace-me \
  TELEGRAM_WEBHOOK_SECRET=replace-me \
  ALLOWED_TELEGRAM_USER_IDS=234392020 \
  SQLITE_DB_PATH=/app/data/app.db \
  SESSION_TTL_DAYS=7 \
  RATE_LIMIT_WINDOW_MS=10000 \
  RATE_LIMIT_MAX_MESSAGES=5
```

注意：

- `BASE_URL` 一定要係 `https://telegram-codex.on99.app`
- 唔好加 `/telegram/webhook`
- `ALLOWED_TELEGRAM_USER_IDS` 支援多個 id，用逗號分隔

### 5. 加 Dokku git remote

喺你本機跑：

```bash
git remote add dokku dokku@your-server:telegram-codex
```

如果已經有 remote，就改返 URL：

```bash
git remote set-url dokku dokku@your-server:telegram-codex
```

### 6. Deploy

喺你本機跑：

```bash
git push dokku main
```

如果你而家唔係 `main` branch，就直接推目前 branch 去 Dokku 嘅 `main`：

```bash
git push dokku HEAD:main
```

### 7. 設定 Telegram webhook

deploy 完之後，喺 Dokku server 跑：

```bash
dokku run telegram-codex node dist/scripts/setWebhook.js
```

呢條 command 會用 Dokku config 入面嘅 `BASE_URL`，所以最後會註冊成：

```bash
https://telegram-codex.on99.app/telegram/webhook
```

### 8. 睇 log

喺 Dokku server 跑：

```bash
dokku logs telegram-codex -t
```

### 9. 確認 running container 入面有冇 `.codex` 檔案

如果 deploy 完之後 `codex exec` 失敗，先查 mount 有冇真係入到 container：

```bash
dokku enter telegram-codex web
ls -la /root/.codex
cat /root/.codex/config.toml
```

你應該要見到：

- `/root/.codex/config.toml`
- `/root/.codex/auth.json`

### 10. 確認 webhook 狀態

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
dokku run telegram-codex node dist/scripts/setWebhook.js
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
dokku run telegram-codex node dist/scripts/setWebhook.js
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
