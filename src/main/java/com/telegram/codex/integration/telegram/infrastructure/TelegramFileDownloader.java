package com.telegram.codex.integration.telegram.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telegram.codex.integration.telegram.domain.TelegramConstants;
import com.telegram.codex.shared.config.AppProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class TelegramFileDownloader {

    private final AppProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TelegramFileDownloader(AppProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public Path downloadFileToTemp(String fileId) {
        TelegramFileResult file = getFile(fileId);
        String filePath = file.filePath();
        try {
            Path tempDir = Files.createTempDirectory("telegram-codex-file-");
            Path outputPath = tempDir.resolve(Path.of(filePath).getFileName().toString());
            HttpRequest request = HttpRequest.newBuilder(
                URI.create(TelegramConstants.TELEGRAM_FILE_API_BASE + properties.getTelegramBotToken() + "/" + filePath)
            ).GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            validateStatusCode(response.statusCode(), "download Telegram file");
            Files.write(outputPath, response.body());
            return outputPath;
        } catch (java.io.IOException | InterruptedException error) {
            throw new IllegalStateException("Failed to download Telegram file", error);
        }
    }

    private TelegramFileResult getFile(String fileId) {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                URI.create(TelegramConstants.TELEGRAM_API_BASE + properties.getTelegramBotToken() + "/getFile?file_id=" + URLEncoder.encode(fileId, StandardCharsets.UTF_8))
            ).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            validateStatusCode(response.statusCode(), "call Telegram getFile");
            TelegramApiResponse<TelegramFileResult> payload = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            validateTelegramResponse(payload);
            TelegramFileResult result = payload.result();
            if (result == null || result.filePath() == null || result.filePath().isBlank()) {
                throw new IllegalStateException("Telegram getFile did not include a file path");
            }
            return result;
        } catch (java.io.IOException | InterruptedException error) {
            throw new IllegalStateException("Failed to call Telegram getFile", error);
        }
    }

    private void validateStatusCode(int statusCode, String operation) {
        if (statusCode / 100 != 2) {
            throw new IllegalStateException("Failed to " + operation + ": HTTP " + statusCode);
        }
    }

    private void validateTelegramResponse(TelegramApiResponse<?> payload) {
        if (payload == null || !Boolean.TRUE.equals(payload.ok())) {
            throw new IllegalStateException("Failed to call Telegram getFile: invalid response");
        }
    }

    private record TelegramApiResponse<T>(
        @JsonProperty("ok") Boolean ok,
        @JsonProperty("result") T result
    ) {
    }

    private record TelegramFileResult(
        @JsonProperty("file_path") String filePath
    ) {
    }
}
