package com.telegramcodex.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String allowedTelegramUserIds = "";
    @NotBlank
    private String baseUrl;
    @Min(1)
    private int codexExecTimeoutSeconds = 300;
    @Min(1)
    private int maxMediaGroupImages = 6;
    @Min(1)
    private int maxPdfPages = 4;
    @Min(1)
    private int mediaGroupWaitMs = 1200;
    @Min(1)
    private int port = 3000;
    @Min(1)
    private int rateLimitMaxMessages = 5;
    @Min(1)
    private long rateLimitWindowMs = 10_000;
    @Min(1)
    private int sessionTtlDays = 7;
    @NotBlank
    private String sqliteDbPath = "./data/app.db";
    @NotBlank
    private String telegramBotToken;
    @NotBlank
    private String telegramWebhookSecret;

    public List<String> allowedTelegramUserIds() {
        if (allowedTelegramUserIds == null || allowedTelegramUserIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(allowedTelegramUserIds.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
    }

    public URI baseUrlUri() {
        return URI.create(baseUrl);
    }

    public Path sqliteDbAbsolutePath() {
        return Path.of(sqliteDbPath).toAbsolutePath().normalize();
    }

    public String getAllowedTelegramUserIds() {
        return allowedTelegramUserIds;
    }

    public void setAllowedTelegramUserIds(String allowedTelegramUserIds) {
        this.allowedTelegramUserIds = allowedTelegramUserIds;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getCodexExecTimeoutSeconds() {
        return codexExecTimeoutSeconds;
    }

    public void setCodexExecTimeoutSeconds(int codexExecTimeoutSeconds) {
        this.codexExecTimeoutSeconds = codexExecTimeoutSeconds;
    }

    public int getMaxMediaGroupImages() {
        return maxMediaGroupImages;
    }

    public void setMaxMediaGroupImages(int maxMediaGroupImages) {
        this.maxMediaGroupImages = maxMediaGroupImages;
    }

    public int getMaxPdfPages() {
        return maxPdfPages;
    }

    public void setMaxPdfPages(int maxPdfPages) {
        this.maxPdfPages = maxPdfPages;
    }

    public int getMediaGroupWaitMs() {
        return mediaGroupWaitMs;
    }

    public void setMediaGroupWaitMs(int mediaGroupWaitMs) {
        this.mediaGroupWaitMs = mediaGroupWaitMs;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getRateLimitMaxMessages() {
        return rateLimitMaxMessages;
    }

    public void setRateLimitMaxMessages(int rateLimitMaxMessages) {
        this.rateLimitMaxMessages = rateLimitMaxMessages;
    }

    public long getRateLimitWindowMs() {
        return rateLimitWindowMs;
    }

    public void setRateLimitWindowMs(long rateLimitWindowMs) {
        this.rateLimitWindowMs = rateLimitWindowMs;
    }

    public int getSessionTtlDays() {
        return sessionTtlDays;
    }

    public void setSessionTtlDays(int sessionTtlDays) {
        this.sessionTtlDays = sessionTtlDays;
    }

    public String getSqliteDbPath() {
        return sqliteDbPath;
    }

    public void setSqliteDbPath(String sqliteDbPath) {
        this.sqliteDbPath = sqliteDbPath;
    }

    public String getTelegramBotToken() {
        return telegramBotToken;
    }

    public void setTelegramBotToken(String telegramBotToken) {
        this.telegramBotToken = telegramBotToken;
    }

    public String getTelegramWebhookSecret() {
        return telegramWebhookSecret;
    }

    public void setTelegramWebhookSecret(String telegramWebhookSecret) {
        this.telegramWebhookSecret = telegramWebhookSecret;
    }
}
