# syntax=docker/dockerfile:1

FROM node:24-bookworm-slim AS build

WORKDIR /app

COPY package.json pnpm-lock.yaml ./
RUN corepack enable && pnpm install --frozen-lockfile

COPY tsconfig.json nest-cli.json jest.config.js .prettierrc ./
COPY src src

RUN pnpm run build

FROM node:24-bookworm-slim AS runtime

WORKDIR /app

COPY .codex-version /tmp/.codex-version

RUN apt-get update -qq && \
    apt-get install --no-install-recommends -y ca-certificates curl git sqlite3 && \
    npm install -g @openai/codex@"$(cat /tmp/.codex-version)" && \
    corepack enable && \
    mkdir -p /app/data /root/.codex && \
    rm -rf /var/lib/apt/lists/* /tmp/.codex-version

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --prod --frozen-lockfile

COPY --from=build /app/dist ./dist

EXPOSE 3000

CMD ["pnpm", "run", "start:prod"]
