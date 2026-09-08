# syntax=docker/dockerfile:1

FROM rust:1-alpine AS build

WORKDIR /app

RUN apk add --no-cache musl-dev

# Cache dependency compilation before the real sources land.
COPY Cargo.toml Cargo.lock ./
RUN mkdir -p src/bin && \
    echo "fn main() {}" > src/main.rs && \
    echo "fn main() {}" > src/bin/task.rs && \
    echo "" > src/lib.rs && \
    cargo build --release --locked && \
    rm -rf src

COPY rustfmt.toml ./
COPY src src

RUN touch src/main.rs src/lib.rs src/bin/task.rs && cargo build --release --locked

FROM node:24-alpine AS runtime

WORKDIR /app

COPY .codex-version /tmp/.codex-version

RUN apk add --no-cache ca-certificates curl g++ git make python3 sqlite && \
    npm install -g @openai/codex@"$(cat /tmp/.codex-version)" && \
    mkdir -p /app/data /root/.codex && \
    rm -f /tmp/.codex-version

COPY --from=build /app/target/release/telegram-codex /usr/local/bin/telegram-codex
COPY --from=build /app/target/release/task /usr/local/bin/task

EXPOSE 3000

CMD ["telegram-codex"]
