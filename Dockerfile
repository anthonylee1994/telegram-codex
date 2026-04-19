# syntax=docker/dockerfile:1

ARG RUBY_VERSION=4.0.2

FROM ruby:${RUBY_VERSION}-slim AS base

WORKDIR /rails

ENV RAILS_ENV=production \
    BUNDLE_DEPLOYMENT=1 \
    BUNDLE_PATH=/usr/local/bundle \
    BUNDLE_WITHOUT=development:test

FROM base AS build

COPY .codex-version /tmp/.codex-version

RUN apt-get update -qq && \
    apt-get install --no-install-recommends -y build-essential ca-certificates curl git libsqlite3-dev libyaml-dev nodejs npm pkg-config poppler-utils sqlite3 && \
    npm install -g @openai/codex@"$(cat /tmp/.codex-version)" && \
    rm -rf /var/lib/apt/lists/* /tmp/.codex-version

COPY Gemfile Gemfile.lock ./
RUN bundle install

COPY . .

FROM base AS runtime

COPY .codex-version /tmp/.codex-version

RUN apt-get update -qq && \
    apt-get install --no-install-recommends -y ca-certificates git nodejs npm poppler-utils sqlite3 && \
    npm install -g @openai/codex@"$(cat /tmp/.codex-version)" && \
    mkdir -p /rails/data /root/.codex && \
    rm -rf /var/lib/apt/lists/* /tmp/.codex-version

COPY --from=build /usr/local/bundle /usr/local/bundle
COPY --from=build /rails /rails
RUN chmod +x /rails/bin/docker-entrypoint

ENTRYPOINT ["/rails/bin/docker-entrypoint"]

EXPOSE 3000

CMD ["./bin/rails", "server", "-b", "0.0.0.0", "-p", "3000"]
