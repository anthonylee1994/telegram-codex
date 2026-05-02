# syntax=docker/dockerfile:1

FROM oven/bun:1.3.4 AS build

WORKDIR /app

COPY package.json bun.lock* ./
RUN bun install --frozen-lockfile

COPY tsconfig.json nest-cli.json jest.config.ts .prettierrc ./
COPY src src

RUN bun run build

FROM oven/bun:1.3.4 AS runtime

WORKDIR /app

COPY .codex-version /tmp/.codex-version

RUN apt-get update -qq && \
    apt-get install --no-install-recommends -y ca-certificates curl git sqlite3 nodejs npm && \
    npm install -g @openai/codex@"$(cat /tmp/.codex-version)" && \
    mkdir -p /app/data /root/.codex && \
    rm -rf /var/lib/apt/lists/* /tmp/.codex-version

COPY package.json bun.lock* ./
RUN bun install --production --frozen-lockfile

COPY --from=build /app/dist ./dist

EXPOSE 3000

CMD ["bun", "run", "start:prod"]
