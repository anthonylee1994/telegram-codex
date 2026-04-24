package com.telegram.codex.integration.telegram.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Component
public class TelegramApiClient {

    private final AppProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TypingStatusManager typingStatusManager;

    public TelegramApiClient(AppProperties properties, ObjectMapper objectMapper, HttpClient httpClient, TypingStatusManager typingStatusManager) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.typingStatusManager = typingStatusManager;
    }

    public void postForm(String methodName, TelegramFormRequest params) {
        try {
            String body = buildFormBody(params);
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiBase() + "/" + methodName))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            validateStatusCode(response.statusCode(), "call Telegram " + methodName);
            TelegramApiResponse<JsonNode> payload = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            validateTelegramResponse(payload, methodName);
        } catch (java.io.IOException | InterruptedException error) {
            throw new IllegalStateException("Failed to call Telegram " + methodName, error);
        }
    }

    public <T> T withTypingStatus(String chatId, Supplier<T> action) {
        return typingStatusManager.withTypingStatus(chatId, id -> sendChatAction(id, "typing"), action);
    }

    public void sendChatAction(String chatId, String action) {
        postForm("sendChatAction", new SendChatActionRequest(chatId, action));
    }

    private String buildFormBody(TelegramFormRequest params) {
        return params.toFormFields().stream()
            .map(entry -> URLEncoder.encode(entry.key(), StandardCharsets.UTF_8) + "=" +
                URLEncoder.encode(entry.value(), StandardCharsets.UTF_8))
            .reduce((a, b) -> a + "&" + b)
            .orElse("");
    }

    private String apiBase() {
        return TelegramConstants.TELEGRAM_API_BASE + properties.getTelegramBotToken();
    }

    private void validateStatusCode(int statusCode, String operation) {
        if (statusCode / 100 != 2) {
            throw new IllegalStateException("Failed to " + operation + ": HTTP " + statusCode);
        }
    }

    private void validateTelegramResponse(TelegramApiResponse<?> payload, String operation) {
        if (payload == null || !Boolean.TRUE.equals(payload.ok())) {
            throw new IllegalStateException("Failed to " + operation + ": invalid response");
        }
    }

    public interface TelegramFormRequest {
        List<FormField> toFormFields();
    }

    public record FormField(String key, String value) {
    }

    public record SendMessageRequest(
        String chatId,
        String text,
        String parseMode,
        String replyMarkup
    ) implements TelegramFormRequest {
        @Override
        public List<FormField> toFormFields() {
            ArrayList<FormField> fields = new ArrayList<>();
            fields.add(new FormField("chat_id", chatId));
            fields.add(new FormField("text", text));
            fields.add(new FormField("parse_mode", parseMode));
            if (replyMarkup != null && !replyMarkup.isBlank()) {
                fields.add(new FormField("reply_markup", replyMarkup));
            }
            return List.copyOf(fields);
        }
    }

    public record SendChatActionRequest(
        String chatId,
        String action
    ) implements TelegramFormRequest {
        @Override
        public List<FormField> toFormFields() {
            return List.of(
                new FormField("chat_id", chatId),
                new FormField("action", action)
            );
        }
    }

    public record SetWebhookRequest(
        String url,
        String secretToken
    ) implements TelegramFormRequest {
        @Override
        public List<FormField> toFormFields() {
            return List.of(
                new FormField("url", url),
                new FormField("secret_token", secretToken)
            );
        }
    }

    public record SetMyCommandsRequest(
        String commands
    ) implements TelegramFormRequest {
        @Override
        public List<FormField> toFormFields() {
            return List.of(new FormField("commands", commands));
        }
    }

    private record TelegramApiResponse<T>(
        @JsonProperty("ok") Boolean ok,
        @JsonProperty("result") T result
    ) {
    }
}
