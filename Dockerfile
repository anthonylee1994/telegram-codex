# syntax=docker/dockerfile:1

FROM node:24-alpine AS build

WORKDIR /app

RUN apk add --no-cache g++ make python3

COPY package.json pnpm-lock.yaml ./
RUN corepack enable && pnpm install --frozen-lockfile

COPY tsconfig.json nest-cli.json jest.config.js .prettierrc ./
COPY src src

RUN pnpm run build

FROM node:24-alpine AS runtime

WORKDIR /app

COPY .codex-version /tmp/.codex-version

RUN apk add --no-cache ca-certificates curl g++ git make python3 sqlite && \
    npm install -g @openai/codex@"$(cat /tmp/.codex-version)" && \
    corepack enable && \
    mkdir -p /app/data /root/.codex && \
    rm -f /tmp/.codex-version

COPY package.json pnpm-lock.yaml ./
RUN npm_config_build_from_source=true pnpm install --prod --frozen-lockfile

COPY --from=build /app/dist ./dist

EXPOSE 3000

CMD ["pnpm", "run", "start:prod"]
