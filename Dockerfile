# syntax=docker/dockerfile:1

FROM gradle:9.3.0-jdk25 AS build

WORKDIR /app

COPY .codex-version /tmp/.codex-version

RUN apt-get update -qq && \
    apt-get install --no-install-recommends -y ca-certificates curl git poppler-utils sqlite3 unzip nodejs npm && \
    npm install -g @openai/codex@"$(cat /tmp/.codex-version)" && \
    rm -rf /var/lib/apt/lists/* /tmp/.codex-version

COPY build.gradle settings.gradle gradlew ./
COPY gradle gradle
COPY src src
COPY bin bin

RUN gradle bootJar --no-daemon

FROM eclipse-temurin:25-jre AS runtime

WORKDIR /app

COPY .codex-version /tmp/.codex-version

RUN apt-get update -qq && \
    apt-get install --no-install-recommends -y ca-certificates curl git poppler-utils sqlite3 unzip nodejs npm && \
    npm install -g @openai/codex@"$(cat /tmp/.codex-version)" && \
    mkdir -p /app/data /root/.codex && \
    rm -rf /var/lib/apt/lists/* /tmp/.codex-version

COPY --from=build /app/build/libs/telegram-codex-1.0.0.jar /app/telegram-codex.jar
COPY --from=build /app/bin /app/bin

RUN chmod +x /app/bin/telegram-codex

EXPOSE 3000

CMD ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/telegram-codex.jar"]
