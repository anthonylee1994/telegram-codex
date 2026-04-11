# telegram-codex

Telegram bot backend using Codex CLI with SQLite session memory.

## Local setup

1. Copy `.env.example` to `.env`.
2. Create a bot with BotFather and fill `TELEGRAM_BOT_TOKEN`.
3. Fill `BASE_URL`, `TELEGRAM_WEBHOOK_SECRET`, and `ALLOWED_TELEGRAM_USER_IDS` (comma-separated).
4. Make sure the machine can run `codex exec` using your existing `~/.codex/config.toml` and `~/.codex/auth.json`.
5. Install dependencies with `pnpm install`.

## Local run

```bash
pnpm dev
```

## Set webhook

`pnpm set-webhook` always uses `BASE_URL` from `.env`, so make sure `BASE_URL` is already set correctly before running it.

```bash
pnpm set-webhook
```

## Checks

```bash
pnpm type-check
pnpm lint
pnpm format
pnpm test
```

## Docker

```bash
docker compose up --build
```

## Dokku deploy

Assumptions:

- Dokku app name: `telegram-codex`
- Domain: `telegram-codex.on99.app`
- Dokku server user: `dokku`

### 1. Create the app

Run on the Dokku server:

```bash
dokku apps:create telegram-codex
dokku domains:set telegram-codex telegram-codex.on99.app
```

### 2. Prepare persistent storage

This project needs two persistent mounts:

- `/app/data` for SQLite
- `/root/.codex` for `codex exec` auth/config

Run on the Dokku server:

```bash
sudo mkdir -p /var/lib/dokku/data/storage/telegram-codex/data
sudo mkdir -p /var/lib/dokku/data/storage/telegram-codex/codex

dokku storage:mount telegram-codex /var/lib/dokku/data/storage/telegram-codex/data:/app/data
dokku storage:mount telegram-codex /var/lib/dokku/data/storage/telegram-codex/codex:/root/.codex
```

### 3. Copy Codex auth files onto the server

You must put these two files into the Dokku storage mount:

- `config.toml`
- `auth.json`

From your local machine:

```bash
scp ~/.codex/config.toml dokku@your-server:/tmp/config.toml
scp ~/.codex/auth.json dokku@your-server:/tmp/auth.json
```

Then SSH into the server and move them into place:

```bash
sudo mv /tmp/config.toml /var/lib/dokku/data/storage/telegram-codex/codex/config.toml
sudo mv /tmp/auth.json /var/lib/dokku/data/storage/telegram-codex/codex/auth.json
sudo chown -R dokku:dokku /var/lib/dokku/data/storage/telegram-codex
```

Expected final paths on the server:

```bash
/var/lib/dokku/data/storage/telegram-codex/codex/config.toml
/var/lib/dokku/data/storage/telegram-codex/codex/auth.json
```

Inside the container they will appear as:

```bash
/root/.codex/config.toml
/root/.codex/auth.json
```

### 4. Set app config

Run on the Dokku server:

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

Notes:

- `BASE_URL` must be exactly `https://telegram-codex.on99.app`
- Do not append `/telegram/webhook`
- `ALLOWED_TELEGRAM_USER_IDS` supports multiple ids separated by commas

### 5. Add Dokku git remote

Run on your local machine:

```bash
git remote add dokku dokku@your-server:telegram-codex
```

If the remote already exists:

```bash
git remote set-url dokku dokku@your-server:telegram-codex
```

### 6. Deploy

Run on your local machine:

```bash
git push dokku main
```

If your branch is not `main`, push the current branch explicitly:

```bash
git push dokku HEAD:main
```

### 7. Set the Telegram webhook

After deployment finishes, run on the Dokku server:

```bash
dokku run telegram-codex node dist/scripts/setWebhook.js
```

That command uses `BASE_URL` from Dokku config, so it will register:

```bash
https://telegram-codex.on99.app/telegram/webhook
```

### 8. Check logs

Run on the Dokku server:

```bash
dokku logs telegram-codex -t
```

### 9. Verify files inside the running container

If `codex exec` fails after deploy, check whether the auth files are actually mounted:

```bash
dokku enter telegram-codex web
ls -la /root/.codex
cat /root/.codex/config.toml
```

You should see both:

- `/root/.codex/config.toml`
- `/root/.codex/auth.json`

### 10. Verify webhook status

Run from anywhere after deploy:

```bash
curl "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getWebhookInfo"
```

You want to see:

- `url` is `https://telegram-codex.on99.app/telegram/webhook`
- no recent `last_error_message`

## Common problems

### `bad webhook: An HTTPS URL must be provided for webhook`

Your `BASE_URL` is wrong. Fix it to:

```bash
https://telegram-codex.on99.app
```

Then rerun:

```bash
dokku config:set telegram-codex BASE_URL=https://telegram-codex.on99.app
dokku run telegram-codex node dist/scripts/setWebhook.js
```

### `Rejected Telegram webhook request with invalid secret`

Usually one of these:

- `TELEGRAM_WEBHOOK_SECRET` changed but webhook was not re-registered
- app restarted with a different env value
- Telegram is still calling an old deployment

Fix:

```bash
dokku config:set telegram-codex TELEGRAM_WEBHOOK_SECRET=your-secret
dokku ps:rebuild telegram-codex
dokku run telegram-codex node dist/scripts/setWebhook.js
```

### `codex exec` fails in production

Usually one of these:

- `/root/.codex/config.toml` is missing
- `/root/.codex/auth.json` is missing
- mount path is wrong
- auth file contents are invalid

Check:

```bash
dokku enter telegram-codex web
ls -la /root/.codex
codex --version
```
